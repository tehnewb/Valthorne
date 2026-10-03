package valthorne.graphics.font;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Finds installed desktop fonts for UI defaults without classpath font resources.
 * Windows uses Segoe UI and Consolas, macOS uses Arial and Menlo, and Linux
 * checks common DejaVu, Liberation and Noto installations. Explicit filesystem
 * overrides use {@code valthorne.ui.defaultFont} and {@code valthorne.ui.codeFont}.
 * Discovery happens at font loading boundaries, never during glyph rendering.
 * Returned files remain owned by the operating system; callers own loaded fonts.
 */
public final class SystemFonts {
    /**
     * Prevents construction of the installed-font locator.
     */
    private SystemFonts() {}

    /**
     * Finds a readable installed regular or monospace font.
     *
     * @param code whether a monospace face is required
     * @return absolute installed font path
     * @throws IllegalStateException if no supported font file is installed
     */
    public static Path find(boolean code) {
        /*
         * Prefer native UI families and inspect only a small deterministic file list.
         * Overrides are validated before loading rather than silently ignored.
         */
        String property = code ? "valthorne.ui.codeFont" : "valthorne.ui.defaultFont";
        String override = System.getProperty(property);
        if (override != null && !override.isBlank()) {
            Path path = Path.of(override).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path) || !Files.isReadable(path))
                throw new IllegalStateException("Unreadable UI font override: " + path);
            return path;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] candidates;
        if (os.startsWith("windows")) {
            String windows = System.getenv("WINDIR");
            Path directory = Path.of(windows == null ? "C:/Windows" : windows, "Fonts");
            candidates = code ? new String[]{directory.resolve("consola.ttf").toString(), directory.resolve("cour.ttf").toString()} : new String[]{directory.resolve("segoeui.ttf").toString(), directory.resolve("tahoma.ttf").toString(), directory.resolve("arial.ttf").toString()};
        } else if (os.contains("mac")) {
            candidates = code ? new String[]{"/System/Library/Fonts/Supplemental/Courier New.ttf", "/System/Library/Fonts/Menlo.ttc", "/Library/Fonts/Courier New.ttf"} : new String[]{"/System/Library/Fonts/Supplemental/Arial.ttf", "/Library/Fonts/Arial.ttf", "/System/Library/Fonts/Helvetica.ttc"};
        } else {
            candidates = code ? new String[]{"/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", "/usr/share/fonts/dejavu/DejaVuSansMono.ttf", "/usr/share/fonts/truetype/liberation2/LiberationMono-Regular.ttf", "/usr/share/fonts/truetype/liberation/LiberationMono-Regular.ttf"} : new String[]{"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/dejavu/DejaVuSans.ttf", "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf", "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf", "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf"};
        }
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path) && Files.isReadable(path)) return path.toAbsolutePath().normalize();
        }
        throw new IllegalStateException("No installed " + (code ? "monospace" : "UI") + " font found; set -D" + property + " to a readable font file");
    }
}
