package valthorne.graphics.font.slug;

import valthorne.graphics.font.TextSelection;

/**
 * Immutable logical-unit selection rectangles from a retained HarfBuzz layout.
 * The private primitive array stores x, y, width, and height for each rectangle;
 * callers borrow the object without accessing or mutating its backing storage.
 */
final class SlugSelection implements TextSelection {
    private final float[] rectangles; // Owned packed visual rectangle coordinates.

    /**
     * Takes ownership of packed rectangles produced by the layout.
     *
     * @param rectangles exclusive packed x/y/width/height storage
     */
    SlugSelection(float[] rectangles) { this.rectangles = rectangles; }

    @Override
    public int size() { return rectangles.length / 4; }

    @Override
    public float left(int index) { return rectangles[index * 4]; }

    @Override
    public float top(int index) { return rectangles[index * 4 + 1]; }

    @Override
    public float width(int index) { return rectangles[index * 4 + 2]; }

    @Override
    public float height(int index) { return rectangles[index * 4 + 3]; }
}
