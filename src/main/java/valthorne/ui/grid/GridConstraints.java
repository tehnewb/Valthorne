package valthorne.ui.grid;


/**
 * Represents the constraints for a grid layout system. This class stores
 * the position and size of a cell in the grid, including its row and column
 * indices and the number of rows and columns it spans.
 *
 * @author Albert Beaupre
 * @since January 30th, 2026
 */
public record GridConstraints(int row, int col, int rowSpan, int colSpan) {

    /**
     * Initializes a {@code GridConstraints} object, ensuring that the row span
     * and column span values are valid by enforcing a minimum value of 1 for both.
     *
     * @param rowSpan the number of rows the grid cell should span; values less than 1 will be adjusted to 1
     * @param colSpan the number of columns the grid cell should span; values less than 1 will be adjusted to 1
     */
    public GridConstraints {
        rowSpan = Math.max(1, rowSpan);
        colSpan = Math.max(1, colSpan);
    }

    /**
     * Constructs a default {@code GridConstraints} object with initial values for
     * row and column indices set to -1, and both row and column spans set to 1.
     * This constructor is useful for setting default or placeholder constraints.
     */
    public GridConstraints() {
        this(-1, -1, 1, 1);
    }

    /**
     * Creates a {@code GridConstraints} instance with the specified row and column indices.
     * The created instance will have a default row span of 1 and column span of 1.
     *
     * @param row the row index for the grid cell
     * @param col the column index for the grid cell
     * @return a new {@code GridConstraints} instance with the specified row and column indices
     * and default spans of 1 for both rows and columns
     */
    public static GridConstraints of(int row, int col) {
        return new GridConstraints(row, col, 1, 1);
    }

    /**
     * Creates a new {@code GridConstraints} instance based on the current row and column indices,
     * but with updated row and column span values.
     *
     * @param rowSpan the number of rows the grid cell should span; must be at least 1
     * @param colSpan the number of columns the grid cell should span; must be at least 1
     * @return a new {@code GridConstraints} instance with the updated row and column spans
     */
    public GridConstraints span(int rowSpan, int colSpan) {
        return new GridConstraints(row, col, rowSpan, colSpan);
    }
}