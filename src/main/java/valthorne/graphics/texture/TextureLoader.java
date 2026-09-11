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
 * The result is decoded pixel data; GPU texture creation is a separate operation.
 *
 * @author Albert Beaupre
 */
public class TextureLoader implements AssetLoader<TextureParameters, TextureData> {

    /**
     * Decodes the selected image source with the requested vertical orientation.
     * Byte-backed sources provide a defensive copy to the decoder. The returned
     * data has its own lifetime and must be released when no longer required.
     *
     * @param parameters image source and vertical-flip setting
     * @return newly decoded texture data
     * @throws NullPointerException  if parameters is null
     * @throws IllegalStateException if the source type is unsupported
     */
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
