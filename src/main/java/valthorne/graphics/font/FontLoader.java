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
 * @author Albert Beaupre
 * @see AssetLoader
 * @see FontParameters
 * @see FontData
 */
public class FontLoader implements AssetLoader<FontParameters, FontData> {

    /**
     * Decodes and packs the requested character range from a path or copied byte
     * source using the configured pixel font size. This creates font data; callers
     * manage its lifetime and separately construct a rendering font when needed.
     *
     * @param parameters source, font size, first character, and character count
     * @return newly loaded font data
     * @throws NullPointerException  if parameters is null
     * @throws IllegalStateException if the source type is unsupported
     */
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
