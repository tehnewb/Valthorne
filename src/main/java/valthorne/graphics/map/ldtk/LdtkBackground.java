package valthorne.graphics.map.ldtk;

import valthorne.graphics.texture.TextureData;

/**
 * Describes a decoded level background and the crop, scale, and placement that
 * LDtk calculated for it. Coordinates use LDtk's top-left-oriented level space;
 * renderers are responsible for converting them to their own coordinate system.
 *
 * @param relativePath project-relative source image path
 * @param topLeftX horizontal placement of the cropped image in level pixels
 * @param topLeftY vertical placement of the cropped image in level pixels
 * @param scaleX horizontal scale calculated by LDtk
 * @param scaleY vertical scale calculated by LDtk
 * @param cropX horizontal source-image crop origin
 * @param cropY vertical source-image crop origin
 * @param cropWidth cropped source width in pixels
 * @param cropHeight cropped source height in pixels
 * @param textureData decoded CPU-side image data owned by the project
 */
public record LdtkBackground(String relativePath, float topLeftX, float topLeftY, float scaleX, float scaleY, int cropX, int cropY, int cropWidth, int cropHeight, TextureData textureData) {
}
