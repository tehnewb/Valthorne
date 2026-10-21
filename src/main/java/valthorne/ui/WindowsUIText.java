package valthorne.ui;

import valthorne.Window;
import valthorne.graphics.Color;
import valthorne.graphics.font.directwrite.DirectWriteBackend;
import valthorne.graphics.font.directwrite.DirectWriteFace;
import valthorne.graphics.font.directwrite.DirectWriteImage;
import valthorne.graphics.font.directwrite.DirectWriteLayout;
import valthorne.graphics.font.directwrite.DirectWriteRenderingSettings;
import valthorne.graphics.font.slug.SlugFont;
import valthorne.graphics.texture.TextureBatch;

import java.util.LinkedHashMap;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER_BINDING;
import static org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window;

/**
 * Root-owned Windows text resources. Native layouts and raster uploads occur only
 * on cache misses; stable text submits ordinary texture quads without COM calls or
 * temporary cache keys. The cache retains at most 256 rasters and 16 MiB of RGBA
 * texels (plus the corresponding retained CPU texture data). Coordinates are mapped
 * to physical framebuffer pixels before native rasterization, including subpixel
 * phase. Existing batch clipping, translation, opacity, and painter order remain active.
 * Native output is display-encoded and must be submitted after scene tone mapping;
 * the batch prevents a second framebuffer sRGB conversion and preserves source-over alpha.
 */
final class WindowsUIText implements AutoCloseable {
    /**
     * Maximum physical RGBA texture bytes retained by one root.
     */
    private static final long CACHE_BYTES = 16L * 1024 * 1024;
    /**
     * Maximum native font collections retained by one root.
     */
    private static final int MAX_FACES = 32;
    /**
     * Maximum physical text images retained by one root.
     */
    private static final int MAX_IMAGES = 256;
    private final DirectWriteBackend backend = new DirectWriteBackend(); // Owned native Windows factories and font faces.
    private final UIRoot root; // Owning viewport supplies exact logical dimensions rather than rounded matrix coefficients.
    private final long window; // Borrowed native HWND used for monitor-specific calibration updates.
    private final LinkedHashMap<SlugFont, DirectWriteFace> faces = new LinkedHashMap<>(8, .75f, true); // At most 32 exact-file native faces keyed by font identity.
    private final LinkedHashMap<WindowsTextKey, WindowsTextTexture> images = new LinkedHashMap<>(32, .75f, true); // Bounded least-recently-used physical raster cache.
    private final WindowsTextKey lookup = new WindowsTextKey(); // Reused hot lookup, never inserted into the cache.
    private final int[] viewport = new int[4]; // Reused native GL viewport snapshot for pixel mapping.
    private final int[] rasterBounds = new int[4]; // Reused full raster margins and bounds before physical clipping.
    private long retainedBytes; // RGBA texture bytes currently retained.

    /**
     * Creates the native owner lazily on first UI measurement or drawing.
     */
    WindowsUIText(UIRoot root) {
        this.root = root;
        window = Window.getAddress() == 0 ? 0 : glfwGetWin32Window(Window.getAddress());
    }

    /**
     * Resolves one native layout from exact font bytes and logical DIP size.
     *
     * @param font borrowed existing UI font
     * @param text text to shape
     * @param size logical DIPs per em
     * @return borrowed native layout
     */
    DirectWriteLayout layout(SlugFont font, String text, float size) {
        backend.updateForWindow(window);
        DirectWriteFace face = faces.get(font);
        if (face == null) {
            face = backend.load(font.copyEncodedData());
            faces.put(font, face);
            if (faces.size() > MAX_FACES) faces.pollFirstEntry().getValue().close();
        }
        return face.layout(text, size);
    }

    /**
     * Draws genuine transparent native grayscale text on an axis-aligned UI surface.
     * Unsupported projections report a fallback reason so callers retain scalable outline drawing.
     *
     * @param batch active texture batch
     * @param font borrowed UI source font
     * @param text shaped line
     * @param x world baseline X before batch translation
     * @param baseline world baseline Y before batch translation
     * @param size logical em size
     * @param color optional explicit foreground, or null for the batch tint
     * @param background explicit final opaque RGB background, or -1 for transparent grayscale
     * @return NONE when handled, or the reason scalable rendering is required
     */
    UITextFallbackReason draw(TextureBatch batch, SlugFont font, String text, float x, float baseline, float size, Color color, int background) {
        var projection = Window.getProjectionMatrix();
        if (projection[1] != 0 || projection[4] != 0 || projection[0] <= 0 || projection[5] <= 0) return UITextFallbackReason.UNSUPPORTED_PROJECTION;
        glGetIntegerv(GL_VIEWPORT, viewport);
        var rootViewport = root.getViewport();
        float worldWidth = rootViewport == null ? Window.getWidth() : rootViewport.getWorldWidth();
        float worldHeight = rootViewport == null ? Window.getHeight() : rootViewport.getWorldHeight();
        float sx = viewport[2] / worldWidth;
        float sy = viewport[3] / worldHeight;
        if (!Float.isFinite(sx) || !Float.isFinite(sy) || sx > 16 || sy > 16) return UITextFallbackReason.UNSUPPORTED_SCALE;
        if (sx <= 0 || sy <= 0) {
            root.recordTextRendering(UITextRenderingMode.NOT_DRAWN, UITextFallbackReason.EMPTY_VIEWPORT, sx, sy);
            return UITextFallbackReason.NONE;
        }
        DirectWriteLayout layout = layout(font, text, size);
        float physicalX = viewport[0] + (x + batch.getTranslationX()) * sx + (projection[12] + 1) * viewport[2] * .5f;
        float physicalTop = viewport[1] + (baseline + layout.baseline() + batch.getTranslationY()) * sy + (projection[13] + 1) * viewport[3] * .5f;
        float phaseX = physicalX - (float) Math.floor(physicalX);
        float phaseY = (float) Math.ceil(physicalTop) - physicalTop;
        layout.rasterBounds(sx, sy, rasterBounds);
        int fullLeft = (int) Math.floor(physicalX) - rasterBounds[0];
        int fullTop = (int) Math.ceil(physicalTop) + rasterBounds[1];
        int cropLeft = Math.max(0, viewport[0] - fullLeft);
        int cropTop = Math.max(0, fullTop - viewport[1] - viewport[3]);
        int cropWidth = Math.min(rasterBounds[2] - cropLeft, viewport[0] + viewport[2] - fullLeft - cropLeft);
        int cropHeight = Math.min(rasterBounds[3] - cropTop, fullTop - cropTop - viewport[1]);
        if (cropWidth <= 0 || cropHeight <= 0) {
            root.recordTextRendering(UITextRenderingMode.NOT_DRAWN, UITextFallbackReason.OUTSIDE_VIEWPORT, sx, sy);
            return UITextFallbackReason.NONE;
        }
        Color selected = color == null ? batch.getColor() : color;
        int rgba = selected.getRed() << 24 | selected.getGreen() << 16 | selected.getBlue() << 8 | selected.getAlpha();
        lookup.set(faces.get(font), text, size, sx, sy, phaseX, phaseY, rgba, backend.revision(), background, cropLeft, cropTop, cropWidth, cropHeight);
        WindowsTextTexture cached = images.get(lookup);
        if (cached == null) {
            batch.flush();
            DirectWriteImage raster = layout.rasterizeRegion(sx, sy, phaseX, phaseY, rgba, cropLeft, cropTop, cropWidth, cropHeight, background < 0 ? null : background);
            int active = glGetInteger(GL_ACTIVE_TEXTURE);
            int binding = glGetInteger(GL_TEXTURE_BINDING_2D);
            int unpack = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
            int alignment = glGetInteger(GL_UNPACK_ALIGNMENT);
            int rowLength = glGetInteger(GL_UNPACK_ROW_LENGTH);
            int skipRows = glGetInteger(GL_UNPACK_SKIP_ROWS);
            int skipPixels = glGetInteger(GL_UNPACK_SKIP_PIXELS);
            try {
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
                glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
                glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
                cached = new WindowsTextTexture(raster);
            } finally {
                glPixelStorei(GL_UNPACK_ALIGNMENT, alignment);
                glPixelStorei(GL_UNPACK_ROW_LENGTH, rowLength);
                glPixelStorei(GL_UNPACK_SKIP_ROWS, skipRows);
                glPixelStorei(GL_UNPACK_SKIP_PIXELS, skipPixels);
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpack);
                glActiveTexture(active);
                glBindTexture(GL_TEXTURE_2D, binding);
            }
            images.put(new WindowsTextKey(lookup), cached);
            retainedBytes += (long) cached.width * cached.height * 4;
            while (images.size() > MAX_IMAGES || retainedBytes > CACHE_BYTES && images.size() > 1) {
                WindowsTextTexture removed = images.pollFirstEntry().getValue();
                retainedBytes -= (long) removed.width * removed.height * 4;
                removed.texture.dispose();
            }
        }
        float left = x - (cached.left + phaseX) / sx;
        float top = baseline + layout.baseline() + (cached.top + phaseY) / sy;
        batch.drawEncoded(cached.texture, left, top - cached.height / sy, cached.width / sx, cached.height / sy);
        UITextRenderingMode mode = switch (cached.antialiasMode) {
            case DEFAULT -> UITextRenderingMode.DIRECTWRITE_DEFAULT;
            case CLEARTYPE -> UITextRenderingMode.DIRECTWRITE_CLEARTYPE;
            case GRAYSCALE -> UITextRenderingMode.DIRECTWRITE_GRAYSCALE;
            case ALIASED -> UITextRenderingMode.DIRECTWRITE_ALIASED;
        };
        root.recordTextRendering(mode, UITextFallbackReason.NONE, sx, sy);
        return UITextFallbackReason.NONE;
    }

    /**
     * Borrows the most recently refreshed native rendering-settings snapshot.
     *
     * @return installed Windows rendering preferences
     */
    DirectWriteRenderingSettings settings() { return backend.settings(); }

    /**
     * Refreshes native system/monitor settings after the caller flushes text draws.
     *
     * @param monitor native HMONITOR, or zero for defaults
     */
    void refresh(long monitor) {
        for (WindowsTextTexture image : images.values()) image.texture.dispose();
        images.clear();
        retainedBytes = 0;
        backend.refreshSettings(monitor);
    }

    @Override
    public void close() {
        for (WindowsTextTexture image : images.values()) image.texture.dispose();
        images.clear();
        faces.clear();
        backend.close();
    }
}
