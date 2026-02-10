package valthorne.graphics.font;

import valthorne.asset.AssetParameters;

/**
 * FontParameters is a record that encapsulates configuration details related to a font asset.
 * It includes properties such as the font's file path, name, size, and character range.
 * This class implements the AssetParameters interface, allowing it to represent
 * a configurable asset with a unique key.
 * <p>
 * Instances of FontParameters are immutable and directly store the values provided
 * during creation, ensuring lightweight and efficient handling of font asset configurations.
 */
public record FontParameters(String path, String name, int fontSize, int firstCharacterIndex, int characterCount) implements AssetParameters {

    /**
     * Creates an instance of FontParameters with a provided path and font size.
     * The name of the font is set to match the path, and default values are used
     * for the first character index and character count properties.
     *
     * @param path     the file path or identifier for the font resource
     * @param fontSize the size of the font to be applied
     */
    public FontParameters(String path, int fontSize) {
        this(path, path, fontSize, 30, 254);
    }

    @Override
    public String key() {
        return name;
    }
}