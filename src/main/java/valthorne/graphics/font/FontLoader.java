package valthorne.graphics.font;

import valthorne.asset.AssetLoader;

/**
 * The FontLoader class is responsible for loading font data using specified font parameters.
 * It implements the AssetLoader interface, utilizing FontParameters as the configuration
 * and returning FontData as the loaded asset type.
 * <p>
 * This class supports loading fonts from both file paths and byte arrays, utilizing the
 * FontSource encapsulated in the provided FontParameters. Depending on the type of
 * FontSource provided, the appropriate loading method is invoked.
 * <p>
 * If an unsupported or unknown FontSource is provided, an IllegalStateException is thrown.
 *
 * @see AssetLoader
 * @see FontParameters
 * @see FontData
 */
public class FontLoader implements AssetLoader<FontParameters, FontData> {

    @Override
    public FontData load(FontParameters parameters) {
        FontSource src = parameters.source();

        if (src instanceof FontSource.PathSource(String path)) {
            return FontData.load(path, parameters.fontSize(), parameters.firstCharacterIndex(), parameters.characterCount());
        }

        if (src instanceof FontSource.BytesSource(byte[] bytes)) {
            return FontData.load(bytes, parameters.fontSize(), parameters.firstCharacterIndex(), parameters.characterCount());
        }

        throw new IllegalStateException("Unknown FontSource: " + src.getClass().getName());
    }
}