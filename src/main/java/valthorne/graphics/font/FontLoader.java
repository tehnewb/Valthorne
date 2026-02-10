package valthorne.graphics.font;

import valthorne.asset.AssetLoader;
import valthorne.graphics.texture.TextureData;

/**
 * Loads a TTF/OTF file with LWJGL STB TrueType, packs a contiguous glyph range into an atlas,
 * and returns a {@link FontData} containing the atlas {@link TextureData} and glyph metrics.
 *
 * <p>
 * This loader does NOT create a {@code Texture} instance. It returns raw {@link TextureData}
 * so your asset system can decide when/how to upload to OpenGL.
 * </p>
 *
 * @author Albert Beaupre
 * @since January 29th, 2026
 */
public class FontLoader implements AssetLoader<FontParameters, FontData> {

    @Override
    public FontData load(FontParameters parameters) {
        return FontData.load(parameters.path(), parameters.fontSize(), parameters.firstCharacterIndex(), parameters.characterCount());
    }

}
