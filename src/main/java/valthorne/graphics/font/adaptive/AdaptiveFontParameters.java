package valthorne.graphics.font.adaptive;

import valthorne.graphics.font.FontSource;
import valthorne.graphics.font.FontSource.PathSource;
import valthorne.graphics.font.FontSource.BytesSource;

import valthorne.asset.AssetParameters;
import valthorne.io.file.ValthorneFiles;
import valthorne.io.file.ValthorneFileException;

/**
 * Describes an encoded font source, asset name, initial logical em size, and character range.
 * Construction validates a non-null source, nonblank name, positive font size, and
 * bounded Unicode prewarm range.
 * Default factories prewarm printable ASCII; the renderer caps eager work at 256 characters.
 * <p>Only name supplies the shared asset-cache key. Use distinct names for source,
 * size, or range variants that must coexist. Path factories defer file reads,
 * byte factories copy input, and classpath factories read resources immediately.
 *
 * @param source encoded font source
 * @param name nonblank shared asset-cache key
 * @param fontSize positive initial logical em size
 * @param firstCharacterIndex first character in the prewarm range
 * @param characterCount positive number of prewarm characters
 * @author Albert Beaupre
 */
public record AdaptiveFontParameters(FontSource source, String name, int fontSize, int firstCharacterIndex, int characterCount) implements AssetParameters {

    /**
     * Validates the initialization of a AdaptiveFontParameters record to ensure that all required fields
     * are non-null, non-blank if applicable, and greater than zero when constraints are specified.
     *
     * @param source              the source from which the font is loaded, must not be null
     * @param name                the name of the font, must not be null or blank
     * @param fontSize            the size of the font, must be greater than 0
     * @param firstCharacterIndex the index of the first character in the font
     * @param characterCount      the number of characters in the font, must be greater than 0
     * @throws IllegalArgumentException if source is null, name is null or blank, fontSize is less than or equal to 0, or characterCount is less than or equal to 0
     */
    public AdaptiveFontParameters {
        if (source == null) throw new IllegalArgumentException("source cannot be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be null/blank");
        if (fontSize < 1 || fontSize > 4096) throw new IllegalArgumentException("fontSize must be between 1 and 4096");
        if (firstCharacterIndex < 0 || characterCount < 1 || characterCount > 65536 || (long) firstCharacterIndex + characterCount > 0x110000)
            throw new IllegalArgumentException("Invalid Unicode prewarm range");
    }

    /**
     * Creates a new {@code AdaptiveFontParameters} instance given the path to the font file
     * and the desired font size. This method uses default values for the first character
     * index and character count.
     *
     * @param path     the file path pointing to the font resource, must not be null or blank
     * @param fontSize the size of the font, must be greater than 0
     * @return a new {@code AdaptiveFontParameters} instance representing the font configuration
     * @throws IllegalArgumentException if the path is null, blank, or if fontSize is less than or equal to 0
     */
    public static AdaptiveFontParameters fromPath(String path, int fontSize) {
        /*
         * Retain the standard asset factory contract without allocating native resources.
         */
        return new AdaptiveFontParameters(new PathSource(path), path, fontSize, 32, 95);
    }

    /**
     * Creates a new {@code AdaptiveFontParameters} instance from the specified file path, font name,
     * font size, first character index, and character count.
     *
     * @param path      the file path pointing to the font resource; must not be null or blank
     * @param name      the name of the font; must not be null or blank
     * @param fontSize  the size of the font; must be greater than 0
     * @param firstChar the index of the first character in the font
     * @param count     the number of characters in the font; must be greater than 0
     * @return a new {@code AdaptiveFontParameters} instance representing the font configuration
     * @throws IllegalArgumentException if the path is null or blank,
     *                                  if name is null or blank,
     *                                  if fontSize is less than or equal to 0,
     *                                  or if count is less than or equal to 0
     */
    public static AdaptiveFontParameters fromPath(String path, String name, int fontSize, int firstChar, int count) {
        /*
         * Retain the standard asset factory contract without allocating native resources.
         */
        return new AdaptiveFontParameters(new PathSource(path), name, fontSize, firstChar, count);
    }

    /**
     * Creates a new {@code AdaptiveFontParameters} instance from the specified byte array, font name,
     * and font size. This method uses default values for the first character index and character count.
     *
     * @param bytes    a byte array containing the font data; must not be null or empty
     * @param name     the name of the font; must not be null or blank
     * @param fontSize the size of the font; must be greater than 0
     * @return a new {@code AdaptiveFontParameters} instance representing the font configuration
     * @throws IllegalArgumentException if bytes are null or empty, if name is null or blank,
     *                                  or if fontSize is less than or equal to 0
     */
    public static AdaptiveFontParameters fromBytes(byte[] bytes, String name, int fontSize) {
        /*
         * Retain the standard asset factory contract without allocating native resources.
         */
        return new AdaptiveFontParameters(new BytesSource(bytes), name, fontSize, 32, 95);
    }

    /**
     * Creates a new {@code AdaptiveFontParameters} instance from the specified byte array, font name,
     * font size, first character index, and character count.
     *
     * @param bytes     a byte array containing the font data; must not be null or empty
     * @param name      the name of the font; must not be null or blank
     * @param fontSize  the size of the font; must be greater than 0
     * @param firstChar the index of the first character in the font
     * @param count     the number of characters in the font; must be greater than 0
     * @return a new {@code AdaptiveFontParameters} instance representing the font configuration
     * @throws IllegalArgumentException if bytes are null or empty, if name is null or blank,
     *                                  if fontSize is less than or equal to 0, or if count is less than or equal to 0
     */
    public static AdaptiveFontParameters fromBytes(byte[] bytes, String name, int fontSize, int firstChar, int count) {
        /*
         * Retain the standard asset factory contract without allocating native resources.
         */
        return new AdaptiveFontParameters(new BytesSource(bytes), name, fontSize, firstChar, count);
    }

    /**
     * Creates a new {@code AdaptiveFontParameters} instance from a resource path on the classpath,
     * along with the specified font name and font size. This method uses default values
     * for the first character index and character count.
     *
     * @param resourcePath the path to the font resource located on the classpath; must not be null or blank
     * @param name         the name of the font; must not be null or blank
     * @param fontSize     the size of the font; must be greater than 0
     * @return a new {@code AdaptiveFontParameters} instance representing the font configuration
     * @throws IllegalArgumentException if resourcePath is null or blank, if name is null or blank,
     *                                  or if fontSize is less than or equal to 0
     * @throws ValthorneFileException if the classpath resource
     *         is missing or cannot be read; resource bytes are read synchronously
     */
    public static AdaptiveFontParameters fromClasspath(String resourcePath, String name, int fontSize) {
        /*
         * Retain the standard asset factory contract without allocating native resources.
         */
        return fromBytes(ValthorneFiles.readBytes(resourcePath), name, fontSize);
    }

    /**
     * Creates a new {@code AdaptiveFontParameters} instance from the specified resource path on the
     * classpath, font name, font size, first character index, and character count.
     *
     * @param resourcePath the path to the font resource located on the classpath; must not be null or blank
     * @param name         the name of the font; must not be null or blank
     * @param fontSize     the size of the font; must be greater than 0
     * @param firstChar    the index of the first character in the font
     * @param count        the number of characters in the font; must be greater than 0
     * @return a new {@code AdaptiveFontParameters} instance representing the font configuration
     * @throws IllegalArgumentException if resourcePath is null or blank, if name is null or blank,
     *                                  if fontSize is less than or equal to 0, or if count is less than or equal to 0
     * @throws ValthorneFileException if the classpath resource
     *         is missing or cannot be read; resource bytes are read synchronously
     */
    public static AdaptiveFontParameters fromClasspath(String resourcePath, String name, int fontSize, int firstChar, int count) {
        /*
         * Retain the standard asset factory contract without allocating native resources.
         */
        return fromBytes(ValthorneFiles.readBytes(resourcePath), name, fontSize, firstChar, count);
    }

    @Override
    public String key() {
        return name;
    }
}
