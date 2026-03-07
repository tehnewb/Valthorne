package valthorne.graphics;

import valthorne.graphics.texture.TextureBatch;

/**
 * Defines an interface for objects that can be drawn to a specific position
 * and size on a rendering surface. Implementations of this interface
 * should handle the logic for rendering visual elements such as textures,
 * shapes, or other drawable components.
 * <p>
 * The {@code draw} method provides the necessary parameters to define
 * the position and dimensions of the drawable object. Implementing classes
 * are expected to use these parameters to render their content at the
 * specified location with the given width and height.
 *
 * @author Albert Beaupre
 * @since December 22nd, 2025
 */
public interface Drawable {

    /**
     * Renders the drawable object to the specified position and size on the rendering surface.
     *
     * @param batch the batch used for drawing, which manages rendering operations
     * @param x the x-coordinate of the position where the object should be drawn
     * @param y the y-coordinate of the position where the object should be drawn
     * @param width the width of the area where the object should be drawn
     * @param height the height of the area where the object should be drawn
     */
    void draw(TextureBatch batch, float x, float y, float width, float height);

    /**
     * Retrieves the width of the drawable object.
     *
     * @return the width of the object as a floating-point value.
     */
    float getWidth();

    /**
     * Retrieves the height of the drawable object.
     *
     * @return the height of the object as a floating-point value.
     */
    float getHeight();
}
