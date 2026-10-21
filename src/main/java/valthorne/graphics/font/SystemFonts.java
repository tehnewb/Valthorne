package valthorne.graphics.font;

import valthorne.io.file.ValthorneFiles;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the font paths used by existing UI and theme loaders. Both ordinary
 * and code text default to bundled JetBrains Mono. Explicit filesystem overrides
 * use {@code valthorne.ui.defaultFont} and {@code valthorne.ui.codeFont}.
 * The bundled resource is extracted once through ValthorneFiles for APIs that
 * require filesystem paths and is deleted on JVM exit. Callers own loaded fonts.
 */
public final class SystemFonts {
    /**
     * Shared bundled font path for existing filesystem-based font loaders.
     */
    private static final Path DEFAULT_FONT = ValthorneFiles.extractToTempFile("valthorne/fonts/jetbrains-mono/JetBrainsMono-Regular.ttf");

    /**
     * Prevents construction of the font path resolver.
     */
    private SystemFonts() {}

    /**
     * Resolves the bundled default or an explicitly configured font file.
     *
     * @param code whether the independent code-font override should be consulted
     * @return absolute font path
     * @throws IllegalStateException if an explicit override cannot be read
     */
    public static Path find(boolean code) {
        /*
         * Existing loaders share the bundled path rather than selecting different
         * installed fonts on each platform. Invalid explicit overrides still fail.
         */
        String property = code ? "valthorne.ui.codeFont" : "valthorne.ui.defaultFont";
        String override = System.getProperty(property);
        if (override != null && !override.isBlank()) {
            Path path = Path.of(override).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path) || !Files.isReadable(path))
                throw new IllegalStateException("Unreadable UI font override: " + path);
            return path;
        }
        return DEFAULT_FONT;
    }
}
