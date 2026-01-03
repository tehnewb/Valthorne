package valthorne.graphics.font;

import valthorne.asset.AssetParameters;

public class FontParameters implements AssetParameters {

    private final String name;
    private final int fontSize;
    private final int firstCharacterIndex;
    private final int characterCount;

    public FontParameters(String name, int fontSize, int firstCharacterIndex, int characterCount) {
        this.name = name;
        this.fontSize = fontSize;
        this.firstCharacterIndex = firstCharacterIndex;
        this.characterCount = characterCount;
    }

    public FontParameters(String name, int fontSize) {
        this(name, fontSize, 30, 254);
    }

    public int getFirstCharacterIndex() {
        return firstCharacterIndex;
    }

    public int getCharacterCount() {
        return characterCount;
    }

    public int getFontSize() {
        return fontSize;
    }

    @Override
    public String key() {
        return name;
    }
}
