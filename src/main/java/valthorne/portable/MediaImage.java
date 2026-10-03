package valthorne.portable;

/**
 * Owned decoded image resource, released by {@link #close()} on the application
 * thread. Drawing borrows the current 2D overlay and does not transfer ownership.
 */
public interface MediaImage extends AutoCloseable {
    /**
     * Returns the decoded image width.
     *
     * @return width in image pixels
     */
    int width();

    /**
     * Returns the decoded image height.
     *
     * @return height in image pixels
     */
    int height();

    /**
     * Draws the full image in the current overlay using CSS-pixel coordinates.
     *
     * @param x left coordinate
     * @param y top coordinate
     * @param width destination width
     * @param height destination height
     * @param alpha opacity between zero and one
     */
    void draw(float x, float y, float width, float height, float alpha);

    @Override
    void close();
}
