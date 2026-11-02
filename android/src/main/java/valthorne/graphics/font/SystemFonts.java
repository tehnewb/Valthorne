package valthorne.graphics.font;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Locates Android's installed scalable fonts. Explicit Valthorne font properties
 * retain their existing filesystem semantics; application fonts can also be loaded
 * from packaged resources through the ordinary asset loaders.
 */
public final class SystemFonts {
    /**
     * Prevents constructing a platform font locator.
     */
    private SystemFonts() {
    }

    /**
     * Resolves an installed proportional or monospace font.
     * @param code whether a monospace face is requested
     * @return readable Android font path
     */
    public static Path find(boolean code) {
        String override = System.getProperty(code ? "valthorne.ui.codeFont" : "valthorne.ui.defaultFont");
        if (override != null && !override.isBlank()) return Path.of(override);
        String[] names = code ? new String[]{"DroidSansMono.ttf", "NotoSansMono-Regular.ttf", "Roboto-Regular.ttf"} : new String[]{"Roboto-Regular.ttf", "NotoSans-Regular.ttf", "RobotoStatic-Regular.ttf"};
        for (String name : names) {
            Path path = Path.of("/system/fonts", name);
            if (Files.isReadable(path)) return path;
        }
        throw new IllegalStateException("No readable Android system font; configure valthorne.ui.defaultFont");
    }
}
