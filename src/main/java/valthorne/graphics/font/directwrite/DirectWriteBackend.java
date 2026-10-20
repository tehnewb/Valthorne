package valthorne.graphics.font.directwrite;

import static valthorne.graphics.font.directwrite.WindowsTextAbi.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static valthorne.graphics.font.directwrite.WindowsCom.*;

/**
 * Owns the installed Microsoft DirectWrite, Direct2D, and Windows Imaging Component
 * factories for one UI renderer. Shaping, font fallback, metrics, hit testing, hinting,
 * and rasterization are performed by Windows. Transparent output uses native grayscale
 * antialiasing; an explicitly opaque target can use native ClearType without reducing
 * its RGB samples to a scalar alpha. This is the DirectWrite/Direct2D rendering path,
 * not a promise to reproduce another Windows application's choice of API or settings.
 *
 * <p>Confine construction, layout, rasterization, and disposal to one thread. Fonts
 * and layouts are backend-owned and released before the factories. No native DLL is
 * redistributed: the JDK foreign linker calls the operating system's own libraries.
 * Windows 10 or newer and a 64-bit JDK 25 are required. Non-Windows callers should
 * check {@link #isSupported()} and retain the existing scalable renderer.</p>
 *
 * <p>Em sizes are device-independent pixels (1/96 inch), never points. Thus 12 pt is
 * 16 DIPs, and is 16 physical pixels only at 96 DPI. Rasterization receives the actual
 * framebuffer pixels per DIP separately. {@link #refreshSettings(long)} refreshes
 * monitor-specific rendering parameters and the system font-smoothing preference.</p>
 *
 * <pre>{@code
 * try (DirectWriteBackend backend = new DirectWriteBackend()) {
 *     DirectWriteFace face = backend.load(fontBytes);
 *     DirectWriteLayout layout = face.layout("Windows native text", 16);
 *     float caretX = layout.caretX(7);
 *     // Rasterize on a cache miss; reuse the resulting image for subsequent frames.
 * }
 * }</pre>
 */
public final class DirectWriteBackend implements AutoCloseable {
    private final Thread thread = Thread.currentThread(); // Native apartment and mutable cache ownership thread.
    private final WindowsCom com; // Native apartment and DLL ownership for this renderer.
    private final MemorySegment factory; // Owned IDWriteFactory3 shaping and collection factory.
    private final MemorySegment d2d; // Owned ID2D1Factory for native software bitmap rendering.
    private final MemorySegment wic; // Owned IWICImagingFactory for transparent premultiplied bitmap targets.
    private final List<DirectWriteFace> faces = new ArrayList<>(); // Fonts released before the factory/apartment.
    private MemorySegment renderingParams = MemorySegment.NULL; // Current monitor/system rendering parameters.
    private boolean smoothing; // Snapshot of Windows' font smoothing preference.
    private boolean clearType; // Whether Windows requests ClearType for eligible opaque targets.
    private int revision; // Invalidates cached rasters when monitor or system parameters change.
    private long settingsFingerprint; // Native rendering properties used to avoid unnecessary raster invalidation.
    private long nextSettingsCheck; // Monotonic deadline for bounded system/monitor preference polling.
    private boolean closed; // Prevents native calls after factory/apartment disposal.

    /**
     * Creates native factories using the installed Windows components.
     *
     * @throws UnsupportedOperationException on another platform or a 32-bit JVM
     * @throws IllegalStateException if a required Windows API fails
     */
    public DirectWriteBackend() {
        if (!isSupported()) throw new UnsupportedOperationException("DirectWrite requires 64-bit Windows");
        com = new WindowsCom();
        MemorySegment write = MemorySegment.NULL;
        MemorySegment draw = MemorySegment.NULL;
        MemorySegment imaging = MemorySegment.NULL;
        try (Arena scope = Arena.ofConfined()) {
            MemorySegment output = scope.allocate(ADDRESS);
            check((int) com.function("dwrite", "DWriteCreateFactory", INT, INT, ADDRESS, ADDRESS)
                    .invoke(0, guid(scope, "9a1b41c3-d3bb-466a-87fc-fe67556a3b65"), output), "DWriteCreateFactory");
            write = output.get(ADDRESS, 0);
            check((int) com.function("d2d1", "D2D1CreateFactory", INT, INT, ADDRESS, ADDRESS, ADDRESS)
                    .invoke(0, guid(scope, "06152247-6f50-465a-9245-118bfd3b6007"), MemorySegment.NULL, output), "D2D1CreateFactory");
            draw = output.get(ADDRESS, 0);
            check((int) com.function("ole32", "CoCreateInstance", INT, ADDRESS, ADDRESS, INT, ADDRESS, ADDRESS)
                    .invoke(guid(scope, "cacaf262-9370-4615-a13b-9f5539da4c0a"), MemorySegment.NULL, 1,
                            guid(scope, "ec5ec8a9-c395-4314-9c77-54d7a935ff70"), output), "CoCreateInstance(WIC)");
            imaging = output.get(ADDRESS, 0);
            factory = write;
            d2d = draw;
            wic = imaging;
            refreshSettings(0);
        } catch (RuntimeException | Error failure) {
            com.release(imaging);
            com.release(draw);
            com.release(write);
            com.close();
            throw failure;
        }
    }

    /**
     * Checks the platform before any Windows DLL is loaded.
     *
     * @return whether the required Windows 64-bit ABI is available
     */
    public static boolean isSupported() {
        /*
         * The implementation uses pointer-sized COM slots and Windows UTF-16 APIs.
         */
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows") && ADDRESS.byteSize() == 8;
    }

    /**
     * Converts typographic point sizes to device-independent pixels.
     *
     * @param points finite nonnegative point size (72 points per inch)
     * @return DIPs at 96 DIPs per inch
     */
    public static float pointsToDips(float points) {
        /*
         * Physical DPI is applied later, exactly once, when rasterizing the layout.
         */
        if (!Float.isFinite(points) || points < 0) throw new IllegalArgumentException("Invalid point size");
        return points * (96f / 72f);
    }

    /**
     * Loads the first face from exact encoded font bytes into a private native
     * collection. A temporary font file is retained until the face is closed, so
     * DirectWrite can lazily access its tables without changing installed fonts.
     *
     * @param bytes TrueType/OpenType bytes
     * @return backend-owned font face
     */
    public DirectWriteFace load(byte[] bytes) {
        requireOpen();
        DirectWriteFace face = new DirectWriteFace(this, bytes);
        faces.add(face);
        return face;
    }

    /**
     * Removes a disposed font from backend ownership so preview/theme changes do
     * not retain closed font objects for the entire root lifetime.
     *
     * @param face disposed owned face
     */
    void forget(DirectWriteFace face) { faces.remove(face); }

    /**
     * Refreshes Windows rendering preferences and monitor-specific parameters.
     * Existing layouts remain valid; cached raster images must track {@link #revision()}.
     *
     * @param monitor native HMONITOR, or zero for Windows default parameters
     */
    public void refreshSettings(long monitor) {
        requireOpen();
        try (Arena scope = Arena.ofConfined()) {
            MemorySegment output = scope.allocate(ADDRESS);
            if (monitor == 0) com.hr(factory, WRITE_CREATE_RENDERING_PARAMS, new MemoryLayout[]{ADDRESS}, output);
            else com.hr(factory, WRITE_CREATE_MONITOR_PARAMS, new MemoryLayout[]{ADDRESS, ADDRESS}, MemorySegment.ofAddress(monitor), output);
            MemorySegment candidate = output.get(ADDRESS, 0);
            MemorySegment value = scope.allocate(INT);
            WindowsFunction parameters = com.function("user32", "SystemParametersInfoW", INT, INT, INT, ADDRESS, INT);
            try {
                if ((int) parameters.invoke(0x004a, 0, value, 0) == 0) throw new IllegalStateException("Cannot query Windows font smoothing");
                boolean nextSmoothing = value.get(INT, 0) != 0;
                if ((int) parameters.invoke(0x200a, 0, value, 0) == 0) throw new IllegalStateException("Cannot query Windows font smoothing type");
                boolean nextClearType = value.get(INT, 0) == 2;
                long fingerprint = renderingFingerprint(candidate, scope);
                if (renderingParams.address() == 0 || fingerprint != settingsFingerprint || smoothing != nextSmoothing || clearType != nextClearType) {
                    com.release(renderingParams);
                    renderingParams = candidate;
                    candidate = MemorySegment.NULL;
                    settingsFingerprint = fingerprint;
                    smoothing = nextSmoothing;
                    clearType = nextClearType;
                    revision++;
                }
            } finally {
                com.release(candidate);
            }
        }
    }

    /**
     * Polls monitor/calibration changes at most once per second. Unchanged native
     * parameter values preserve cached raster images instead of rerasterizing them.
     *
     * @param window native HWND, or zero to retain default-monitor parameters
     */
    public void updateForWindow(long window) {
        requireOpen();
        long now = System.nanoTime();
        if (nextSettingsCheck != 0 && now - nextSettingsCheck < 0) return;
        nextSettingsCheck = now + 1_000_000_000L;
        long monitor = window == 0 ? 0 : ((MemorySegment) com.function("user32", "MonitorFromWindow", ADDRESS, ADDRESS, INT)
                .invoke(MemorySegment.ofAddress(window), 2)).address();
        refreshSettings(monitor);
    }

    /**
     * Fingerprints all DirectWrite rendering parameters, including grayscale
     * contrast, grid fitting, and the extended native rendering mode.
     *
     * @param parameters native base rendering parameters
     * @param scope temporary query storage
     * @return stable property fingerprint
     */
    private long renderingFingerprint(MemorySegment parameters, Arena scope) {
        long result = 1;
        for (int slot = 3; slot <= 5; slot++) result = result * 31 + Float.floatToIntBits((float) com.call(parameters, slot, FLOAT, new MemoryLayout[0]));
        for (int slot = 6; slot <= 7; slot++) result = result * 31 + (int) com.call(parameters, slot, INT, new MemoryLayout[0]);
        MemorySegment output = scope.allocate(ADDRESS);
        int status = (int) com.call(parameters, UNKNOWN_QUERY, INT, new MemoryLayout[]{ADDRESS, ADDRESS}, guid(scope, "b7924baa-391b-412a-8c5c-e44cc2d867dc"), output);
        if (status == 0x80004002) return result;
        check(status, "QueryInterface(IDWriteRenderingParams3)");
        MemorySegment extended = output.get(ADDRESS, 0);
        try {
            result = result * 31 + Float.floatToIntBits((float) com.call(extended, 8, FLOAT, new MemoryLayout[0]));
            result = result * 31 + (int) com.call(extended, 9, INT, new MemoryLayout[0]);
            return result * 31 + (int) com.call(extended, 10, INT, new MemoryLayout[0]);
        } finally {
            com.release(extended);
        }
    }

    /**
     * Returns the rendering-settings generation used in raster cache keys.
     *
     * @return current settings generation
     */
    public int revision() { return revision; }

    /**
     * Borrows the native COM owner for related backend objects.
     *
     * @return thread-confined COM access
     */
    WindowsCom com() { requireOpen(); return com; }

    /**
     * Borrows the DirectWrite factory.
     *
     * @return live IDWriteFactory3
     */
    MemorySegment factory() { requireOpen(); return factory; }

    /**
     * Borrows the Direct2D factory.
     *
     * @return live ID2D1Factory
     */
    MemorySegment d2d() { requireOpen(); return d2d; }

    /**
     * Borrows the WIC factory.
     *
     * @return live IWICImagingFactory
     */
    MemorySegment wic() { requireOpen(); return wic; }

    /**
     * Borrows the current Windows rendering parameters.
     *
     * @return live IDWriteRenderingParams
     */
    MemorySegment renderingParams() { return renderingParams; }

    /**
     * Chooses real native antialiasing appropriate to target transparency and settings.
     *
     * @param opaque whether the final background is part of the native target
     * @return D2D1_TEXT_ANTIALIAS_MODE value
     */
    int antialiasMode(boolean opaque) { return !smoothing ? 3 : opaque && clearType ? 1 : 2; }

    /**
     * Rejects access after native resources are destroyed.
     */
    void requireOpen() {
        if (closed) throw new IllegalStateException("DirectWrite backend is closed");
        if (Thread.currentThread() != thread) throw new IllegalStateException("DirectWrite backend is thread confined");
    }

    @Override
    public void close() {
        if (closed) return;
        requireOpen();
        RuntimeException failure = null;
        for (int i = faces.size() - 1; i >= 0; i--) {
            try {
                faces.get(i).close();
            } catch (RuntimeException cleanup) {
                if (failure == null) failure = cleanup;
                else failure.addSuppressed(cleanup);
            }
        }
        faces.clear();
        com.release(renderingParams);
        com.release(wic);
        com.release(d2d);
        com.release(factory);
        com.close();
        closed = true;
        if (failure != null) throw failure;
    }
}
