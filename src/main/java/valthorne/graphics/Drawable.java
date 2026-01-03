package valthorne.graphics;

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
     * Renders the drawable object on the rendering surface at the specified position
     * and with the specified dimensions.
     *
     * @param x      The x-coordinate of the top-left corner where the object should be drawn.
     * @param y      The y-coordinate of the top-left corner where the object should be drawn.
     * @param width  The width of the drawable object.
     * @param height The height of the drawable object.
     */
    void draw(float x, float y, float width, float height);

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
