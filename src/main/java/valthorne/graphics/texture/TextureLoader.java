package valthorne.graphics.texture;

import valthorne.asset.AssetLoader;

/**
 * TextureLoader is responsible for loading texture data based on given parameters. It implements
 * the {@code AssetLoader} interface and supports loading textures from both filesystem paths and
 * raw byte arrays.
 * <p>
 * The loading behavior is determined by the {@code TextureParameters}, which specify
 * the source of the texture and other configurations such as whether the image should
 * be flipped vertically during decoding.
 */
public class TextureLoader implements AssetLoader<TextureParameters, TextureData> {

    @Override
    public TextureData load(TextureParameters parameters) {
        TextureSource src = parameters.source();

        if (src instanceof TextureSource.PathSource(String path)) {
            return TextureData.load(path, parameters.flipVertically());
        }

        if (src instanceof TextureSource.BytesSource(byte[] bytes)) {
            return TextureData.load(bytes, parameters.flipVertically());
        }

        throw new IllegalStateException("Unknown TextureSource: " + src.getClass().getName());
    }
}