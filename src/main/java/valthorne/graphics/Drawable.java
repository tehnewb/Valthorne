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
     * Renders a drawable object onto the given {@code TextureBatch} at the specified position,
     * size, region, origin, rotation, and with an optional color tint.
     *
     * @param batch        the {@code TextureBatch} to which the drawable object is rendered
     * @param x            the x-coordinate on the rendering surface where the object should be drawn
     * @param y            the y-coordinate on the rendering surface where the object should be drawn
     * @param width        the width to render the object
     * @param height       the height to render the object
     * @param regionX      the x-coordinate of the texture region within the texture
     * @param regionY      the y-coordinate of the texture region within the texture
     * @param regionWidth  the width of the texture region
     * @param regionHeight the height of the texture region
     * @param originX      the x-coordinate of the origin point for rotation, scaled relative to the object's size
     * @param originY      the y-coordinate of the origin point for rotation, scaled relative to the object's size
     * @param rotation     the rotation angle in degrees to apply to the object, relative to the origin point
     * @param tint         the color tint to apply to the object; if {@code null}, no tint will be applied
     */
    void draw(TextureBatch batch, float x, float y, float width, float height, float regionX, float regionY, float regionWidth, float regionHeight, float originX, float originY, float rotation, Color tint);

    /**
     * Renders a drawable object onto the specified {@code TextureBatch} at the given position
     * and size using the default texture region, origin, rotation, and without a color tint.
     *
     * @param batch  the {@code TextureBatch} used to render the drawable object
     * @param x      the x-coordinate on the rendering surface where the object should be drawn
     * @param y      the y-coordinate on the rendering surface where the object should be drawn
     * @param width  the width to render the object
     * @param height the height to render the object
     */
    default void draw(TextureBatch batch, float x, float y, float width, float height) {
        draw(batch, x, y, width, height, 0f, 0f, getWidth(), getHeight(), 0f, 0f, 0f, null);
    }

    /**
     * Renders a drawable object onto the given {@code TextureBatch} at a specified position, size,
     * and with an optional color tint. The texture region and other parameters are derived
     * automatically.
     *
     * @param batch  the {@code TextureBatch} used to render the drawable object
     * @param x      the x-coordinate on the rendering surface where the object should be drawn
     * @param y      the y-coordinate on the rendering surface where the object should be drawn
     * @param width  the width to render the object
     * @param height the height to render the object
     * @param tint   the color tint to apply to the object; if {@code null}, no tint will be applied
     */
    default void draw(TextureBatch batch, float x, float y, float width, float height, Color tint) {
        draw(batch, x, y, width, height, 0f, 0f, getWidth(), getHeight(), 0f, 0f, 0f, tint);
    }

    /**
     * Renders a drawable object onto the given {@code TextureBatch} at the specified position, size,
     * origin, rotation, and with an optional color tint. The texture region is derived automatically.
     *
     * @param batch    the {@code TextureBatch} used to render the drawable object
     * @param x        the x-coordinate on the rendering surface where the object should be drawn
     * @param y        the y-coordinate on the rendering surface where the object should be drawn
     * @param width    the width to render the object
     * @param height   the height to render the object
     * @param originX  the x-coordinate of the origin point for rotation, scaled relative to the object's size
     * @param originY  the y-coordinate of the origin point for rotation, scaled relative to the object's size
     * @param rotation the rotation angle in degrees to apply to the object, relative to the origin point
     * @param tint     the color tint to apply to the object; if {@code null}, no tint will be applied
     */
    default void draw(TextureBatch batch, float x, float y, float width, float height, float originX, float originY, float rotation, Color tint) {
        draw(batch, x, y, width, height, 0f, 0f, getWidth(), getHeight(), originX, originY, rotation, tint);
    }

    /**
     * Renders a drawable object onto the specified {@code TextureBatch} at the given position,
     * size, and texture region. Other parameters such as origin, rotation, and tint are assigned
     * default values.
     *
     * @param batch        the {@code TextureBatch} used to render the drawable object
     * @param x            the x-coordinate on the rendering surface where the object should be drawn
     * @param y            the y-coordinate on the rendering surface where the object should be drawn
     * @param width        the width to render the object
     * @param height       the height to render the object
     * @param regionX      the x-coordinate of the texture region within the texture
     * @param regionY      the y-coordinate of the texture region within the texture
     * @param regionWidth  the width of the texture region
     * @param regionHeight the height of the texture region
     */
    default void draw(TextureBatch batch, float x, float y, float width, float height, float regionX, float regionY, float regionWidth, float regionHeight) {
        draw(batch, x, y, width, height, regionX, regionY, regionWidth, regionHeight, 0f, 0f, 0f, null);
    }

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
