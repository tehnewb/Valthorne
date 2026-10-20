package valthorne.ui;

import valthorne.graphics.font.directwrite.DirectWriteImage;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureData;

/**
 * Owns one bounded native-text GPU cache entry and its placement metadata.
 * TextureData borrows JVM-owned pixel bytes, which must never be freed with STB.
 */
final class WindowsTextTexture {
    final Texture texture; // Owned OpenGL texture; disposed only after pending batch draws flush.
    final int width; // Physical raster width.
    final int height; // Physical raster height.
    final int left; // Physical left overhang/guard margin.
    final int top; // Physical top overhang/guard margin.

    /**
     * Uploads native text pixels exactly once with no filtering/resampling.
     *
     * @param image native raster with bottom-up straight RGBA bytes
     */
    WindowsTextTexture(DirectWriteImage image) {
        width = image.width();
        height = image.height();
        left = image.left();
        top = image.top();
        texture = new Texture(new TextureData(image.pixels(), width, height));
    }
}
