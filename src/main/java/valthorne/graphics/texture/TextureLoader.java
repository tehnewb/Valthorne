package valthorne.graphics.texture;

import valthorne.asset.AssetLoader;

/**
 * A loader responsible for loading texture asset data using specified parameters.
 * <p>
 * This class implements the {@code AssetLoader} interface and provides functionality
 * to load texture data from file paths defined by {@code TextureParameters}.
 * It uses {@link TextureParameters} to configure the loading process and generates
 * {@link TextureData} that encapsulates the decoded texture information.
 */
public class TextureLoader implements AssetLoader<TextureParameters, TextureData> {

    @Override
    public TextureData load(TextureParameters parameters) {
        return TextureData.load(parameters.path());
    }
}
