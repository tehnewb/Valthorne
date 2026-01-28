package valthorne.graphics.font;

import valthorne.asset.AssetParameters;

/**
 * Represents the parameters required for defining font attributes in a system.
 * This record encapsulates essential font-related properties such as the font's name,
 * size, and the range of characters it covers.
 * <p>
 * This implementation also provides a mechanism to retrieve a unique key for the font,
 * which can be used to identify and manage the font configuration in a system.
 */
public record FontParameters(String name, int fontSize, int firstCharacterIndex, int characterCount) implements AssetParameters {

    public FontParameters(String name, int fontSize) {
        this(name, fontSize, 30, 254);
    }

    @Override
    public String key() {
        return name;
    }
}