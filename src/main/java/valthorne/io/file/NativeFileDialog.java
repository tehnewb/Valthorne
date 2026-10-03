package valthorne.io.file;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.ByteBuffer;
import java.nio.file.Path;

/**
 * Reusable native file-dialog settings backed by LWJGL TinyFileDialogs.
 * Displays a separate OS dialog rather than a Valthorne node. Open selects one
 * file, Save chooses a destination, and folder selection chooses a directory.
 * Cancellation or a backend result with no selection returns {@code null}.
 * Save never writes a file or adds a filename extension; the application owns IO.
 * Native appearance, filters, and overwrite prompts depend on the host platform.
 *
 * <p>Every display call blocks its caller until dismissal. Invoke on the platform's
 * UI thread; calling from a Valthorne button pauses that thread's rendering and
 * updates. TinyFileDialogs uses global native state: confine all dialog calls,
 * including direct LWJGL calls, to the same thread. No worker or synchronization
 * is introduced here. Settings are immutable and retain no native resources.
 * UTF-8 filter buffers are allocated only while displaying and always freed.
 * Native loading and path-conversion failures propagate to the caller.</p>
 *
 * <pre>{@code
 * NativeFileDialog images = NativeFileDialog.builder()
 *         .title("Open image")
 *         .defaultPath(Path.of("assets"))
 *         .filter("Images", "*.png", "*.jpg")
 *         .build();
 * Path selected = images.open();
 * if (selected != null)
 *     loadImage(selected);
 * }</pre>
 */
public final class NativeFileDialog {
    private final String title; // Caption passed to the native dialog.
    private final String defaultPath; // Initial absolute path; null lets the OS choose.
    private final String filterDescription; // Combined file-type label; null uses the backend default.
    private final String[] filterPatterns; // Exclusively owned immutable wildcard configuration.

    /**
     * Takes ownership of validated settings captured by the builder.
     *
     * @param title validated caption
     * @param defaultPath initial path or null
     * @param filterDescription filter label or null
     * @param filterPatterns exclusively owned validated pattern array
     */
    NativeFileDialog(String title, String defaultPath, String filterDescription, String[] filterPatterns) {
        this.title = title;
        this.defaultPath = defaultPath;
        this.filterDescription = filterDescription;
        this.filterPatterns = filterPatterns;
    }

    /**
     * Starts fluent configuration without initializing native libraries.
     *
     * @return a new mutable builder
     */
    public static NativeFileDialogBuilder builder() {
        /*
         * Keep construction independent of native loading so configurations can
         * be prepared before the application's platform UI lifecycle starts.
         */
        return new NativeFileDialogBuilder();
    }

    /**
     * Blocks while the user chooses one existing file.
     *
     * @return selected path, or null when no selection is returned
     */
    public Path open() {
        return showFile(false);
    }

    /**
     * Blocks while the user chooses a save destination. No content is written,
     * no extension is appended, and the destination need not exist.
     *
     * @return chosen destination, or null when no selection is returned
     */
    public Path save() {
        return showFile(true);
    }

    /**
     * Blocks while the user chooses a directory, ignoring file filters.
     *
     * @return selected directory, or null when no selection is returned
     */
    public Path selectFolder() {
        String selected = TinyFileDialogs.tinyfd_selectFolderDialog(title, defaultPath);
        return selected == null ? null : Path.of(selected);
    }

    /**
     * Encodes the configured filter for one synchronous file-dialog operation.
     * Uses native heap memory so arbitrary filter sizes cannot exhaust the LWJGL
     * thread stack. Partially allocated buffers are released on failure as well.
     *
     * @param save whether to choose a destination instead of an existing file
     * @return selected path or null
     */
    private Path showFile(boolean save) {
        PointerBuffer patterns = null;
        ByteBuffer[] encoded = filterPatterns.length == 0 ? null : new ByteBuffer[filterPatterns.length];
        try {
            if (encoded != null) {
                patterns = MemoryUtil.memAllocPointer(encoded.length);
                for (int i = 0; i < encoded.length; i++) {
                    encoded[i] = MemoryUtil.memUTF8(filterPatterns[i]);
                    patterns.put(i, encoded[i]);
                }
            }
            String selected = save
                    ? TinyFileDialogs.tinyfd_saveFileDialog(title, defaultPath, patterns, filterDescription)
                    : TinyFileDialogs.tinyfd_openFileDialog(title, defaultPath, patterns, filterDescription, false);
            return selected == null ? null : Path.of(selected);
        } finally {
            if (encoded != null) {
                for (ByteBuffer buffer : encoded)
                    MemoryUtil.memFree(buffer);
            }
            MemoryUtil.memFree(patterns);
        }
    }
}
