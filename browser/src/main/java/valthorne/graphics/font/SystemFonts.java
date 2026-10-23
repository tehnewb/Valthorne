package valthorne.graphics.font;

import java.nio.file.Path;

/**
 * Resolves browser UI defaults to bundled, redistributable Noto font assets.
 * Browsers cannot inspect installed desktop font files. Overrides retain the
 * existing property names and refer to classpath-relative application assets;
 * actual loading and validation happen through the browser asset reader.
 */
public final class SystemFonts {
    /**
     * Prevents constructing process-wide font locators.
     */
    private SystemFonts() {
    }

    /**
     * Resolves a proportional or monospace font asset for the shared UI pipeline.
     * @param code whether to select a monospace face
     * @return browser asset path
     */
    public static Path find(boolean code) {
        /*
         * Stable bundled defaults make text rendering independent of local OS
         * fonts and let the same Slug compiler run on every browser platform.
         */
        String override = System.getProperty(code ? "valthorne.ui.codeFont" : "valthorne.ui.defaultFont");
        if (override != null && !override.isBlank()) {
            if (override.contains("://")) throw new IllegalArgumentException("UI font overrides must be relative browser asset paths");
            return Path.of(override);
        }
        return Path.of(code ? "fonts/NotoSansMono-Regular.ttf" : "fonts/NotoSans-Regular.ttf");
    }
}
