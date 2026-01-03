package valthorne.graphics.texture;

import valthorne.asset.AssetLoader;

public class TextureLoader implements AssetLoader<TextureParameters, TextureData> {
    @Override
    public TextureData load(TextureParameters parameters) {
        return TextureData.load(parameters.key());
    }
}
