package valthorne.graphics.font;

import valthorne.asset.AssetLoader;

public class FontLoader implements AssetLoader<FontParameters, FontData> {

    @Override
    public FontData load(FontParameters parameters) {
        return FontData.load(parameters.key(), parameters.getFontSize(), parameters.getFirstCharacterIndex(), parameters.getCharacterCount());
    }

}
