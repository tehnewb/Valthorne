package valthorne.graphics.font;

import valthorne.graphics.font.slug.SlugData;

import java.io.IOException;
import java.io.InputStream;

/**
 * Supplies exact, portable UI font data without requiring installed system fonts.
 * The unmodified JetBrains Mono Regular 2.304 resource is read once per classloader
 * and retained as immutable CPU-only SlugData. Each UI root owns its loaded Slug
 * fonts and NanoVG memory; no shared GPU resources are created here.
 * Explicit {@code valthorne.ui.defaultFont} and {@code valthorne.ui.codeFont}
 * filesystem overrides are resolved at root initialization. Application and theme
 * font selections remain the responsibility of the existing UI style cascade.
 * Bundled copyright, authorship, and the complete SIL OFL 1.1 accompany the font
 * under {@code valthorne/fonts/jetbrains-mono/} in distributed artifacts.
 */
public final class DefaultFonts {
    /**
     * Immutable bundled data shared by roots that do not request a file override.
     */
    private static final SlugData BUNDLED = loadBundled();

    /**
     * Prevents construction of the default font resource provider.
     */
    private DefaultFonts() {}

    /**
     * Resolves a root's ordinary or code font before either renderer initializes.
     * An explicit unreadable override fails rather than silently substituting the
     * bundled font. With no override, both roles share the same immutable data.
     * This cold-path method is safe before graphics initialization.
     *
     * @param code whether the independent code-font override should be consulted
     * @return immutable encoded font data, precompiling codepoints 32 through 255
     * @throws IllegalStateException if an explicit override cannot be read
     */
    public static SlugData load(boolean code) {
        /*
         * Installed-font discovery remains available to applications, but defaults
         * must not vary with operating-system font installations.
         */
        String override = System.getProperty(code ? "valthorne.ui.codeFont" : "valthorne.ui.defaultFont");
        return override == null || override.isBlank() ? BUNDLED : SlugData.load(SystemFonts.find(code).toString(), 32, 224);
    }

    /**
     * Reads the exact upstream TTF from the library classpath once. Resource streams
     * work from JARs without extraction or writable filesystem requirements.
     *
     * @return immutable bundled font source
     * @throws IllegalStateException if packaging omitted the font or reading fails
     */
    private static SlugData loadBundled() {
        /*
         * Class initialization safely publishes the immutable data across roots;
         * only encoded CPU bytes are retained, independent of native/GL lifetimes.
         */
        try (InputStream input = DefaultFonts.class.getResourceAsStream("/valthorne/fonts/jetbrains-mono/JetBrainsMono-Regular.ttf")) {
            if (input == null) throw new IllegalStateException("Bundled JetBrains Mono font is missing");
            return SlugData.load(input.readAllBytes(), 32, 224);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to read bundled JetBrains Mono font", failure);
        }
    }
}
