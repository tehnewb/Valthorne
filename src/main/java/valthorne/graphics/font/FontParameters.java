package valthorne.graphics.font;

import valthorne.asset.AssetParameters;

/**
 * Represents the parameters required to define and load a font asset.
 * The parameters include the source of the font data, font name, font size,
 * the index of the first character, and the number of characters available.
 * This record ensures all essential font attributes are immutable and verified.
 *
 * @param source              the source from which the font is loaded, must not be null
 * @param name                the name of the font, must not be null or blank
 * @param fontSize            the size of the font, must be greater than 0
 * @param firstCharacterIndex the index of the first character in the font
 * @param characterCount      the number of characters in the font, must be greater than 0
 * @throws IllegalArgumentException if any argument is invalid
 */
public record FontParameters(FontSource source, String name, int fontSize, int firstCharacterIndex, int characterCount) implements AssetParameters {

    /**
     * Validates the initialization of a FontParameters record to ensure that all required fields
     * are non-null, non-blank if applicable, and greater than zero when constraints are specified.
     *
     * @param source              the source from which the font is loaded, must not be null
     * @param name                the name of the font, must not be null or blank
     * @param fontSize            the size of the font, must be greater than 0
     * @param firstCharacterIndex the index of the first character in the font
     * @param characterCount      the number of characters in the font, must be greater than 0
     * @throws IllegalArgumentException if source is null, name is null or blank, fontSize is less than or equal to 0, or characterCount is less than or equal to 0
     */
    public FontParameters {
        if (source == null) throw new IllegalArgumentException("source cannot be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name cannot be null/blank");
        if (fontSize <= 0) throw new IllegalArgumentException("fontSize must be > 0");
        if (characterCount <= 0) throw new IllegalArgumentException("characterCount must be > 0");
    }

    /**
     * Creates a new {@code FontParameters} instance given the path to the font file
     * and the desired font size. This method uses default values for the first character
     * index and character count.
     *
     * @param path     the file path pointing to the font resource, must not be null or blank
     * @param fontSize the size of the font, must be greater than 0
     * @return a new {@code FontParameters} instance representing the font configuration
     * @throws IllegalArgumentException if the path is null, blank, or if fontSize is less than or equal to 0
     */
    public static FontParameters fromPath(String path, int fontSize) {
        return new FontParameters(new FontSource.PathSource(path), path, fontSize, 30, 254);
    }

    /**
     * Creates a new {@code FontParameters} instance from the specified file path, font name,
     * font size, first character index, and character count.
     *
     * @param path      the file path pointing to the font resource; must not be null or blank
     * @param name      the name of the font; must not be null or blank
     * @param fontSize  the size of the font; must be greater than 0
     * @param firstChar the index of the first character in the font
     * @param count     the number of characters in the font; must be greater than 0
     * @return a new {@code FontParameters} instance representing the font configuration
     * @throws IllegalArgumentException if the path is null or blank,
     *                                  if name is null or blank,
     *                                  if fontSize is less than or equal to 0,
     *                                  or if count is less than or equal to 0
     */
    public static FontParameters fromPath(String path, String name, int fontSize, int firstChar, int count) {
        return new FontParameters(new FontSource.PathSource(path), name, fontSize, firstChar, count);
    }

    /**
     * Creates a new {@code FontParameters} instance from the specified byte array, font name,
     * and font size. This method uses default values for the first character index and character count.
     *
     * @param bytes    a byte array containing the font data; must not be null or empty
     * @param name     the name of the font; must not be null or blank
     * @param fontSize the size of the font; must be greater than 0
     * @return a new {@code FontParameters} instance representing the font configuration
     * @throws IllegalArgumentException if bytes are null or empty, if name is null or blank,
     *                                  or if fontSize is less than or equal to 0
     */
    public static FontParameters fromBytes(byte[] bytes, String name, int fontSize) {
        return new FontParameters(new FontSource.BytesSource(bytes), name, fontSize, 30, 254);
    }

    /**
     * Creates a new {@code FontParameters} instance from the specified byte array, font name,
     * font size, first character index, and character count.
     *
     * @param bytes     a byte array containing the font data; must not be null or empty
     * @param name      the name of the font; must not be null or blank
     * @param fontSize  the size of the font; must be greater than 0
     * @param firstChar the index of the first character in the font
     * @param count     the number of characters in the font; must be greater than 0
     * @return a new {@code FontParameters} instance representing the font configuration
     * @throws IllegalArgumentException if bytes are null or empty, if name is null or blank,
     *                                  if fontSize is less than or equal to 0, or if count is less than or equal to 0
     */
    public static FontParameters fromBytes(byte[] bytes, String name, int fontSize, int firstChar, int count) {
        return new FontParameters(new FontSource.BytesSource(bytes), name, fontSize, firstChar, count);
    }

    @Override
    public String key() {
        return name;
    }
}