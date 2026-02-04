package valthorne.math.geometry;

import valthorne.graphics.Color;
import valthorne.math.Vector2f;

import static org.lwjgl.opengl.GL11.*;

/**
 * The Shape class serves as an abstract base class for defining 2D geometric shapes.
 * It provides core functionality such as color and border management, rendering methods,
 * and movement capabilities, as well as a contract for subclasses to implement behavior specific to each shape type.
 *
 * @author Albert Beaupre
 * @since January 31st, 2026
 */
public abstract class Shape implements Area {

    private Border border;
    private Color color;

    /**
     * Constructs a new Shape instance with a default color of white.
     * The default color is represented as an RGBA value of (1f, 1f, 1f, 1f).
     */
    public Shape() {
        this.color = new Color(1f, 1f, 1f, 1f);
    }

    /**
     * Constructs a new Shape instance with a specified color.
     * If the provided color is null, the default color is set to white,
     * represented as an RGBA value of (1f, 1f, 1f, 1f).
     *
     * @param color the color of the shape. If null, the default color white is assigned.
     */
    public Shape(Color color) {
        this.color = (color == null) ? new Color(1f, 1f, 1f, 1f) : color;
    }

    /**
     * Moves the shape by a specified offset in 2D space.
     * The method adjusts the position of the shape based on the given offset vector.
     * This operation modifies the shape's position without altering its size, rotation, or other properties.
     *
     * @param offset the 2D vector specifying the movement in the x and y directions.
     *               It represents the amount by which the shape's position should be shifted.
     */
    public abstract void move(Vector2f offset);

    /**
     * Renders the 2D shape using a triangle fan for its geometry.
     * The method calculates the centroid of the shape from its vertices and uses
     * the centroid as the first point for the triangle fan. It then iterates
     * through the vertices, rendering the shape by connecting the points.
     * This assumes the shape has a defined set of points used for rendering.
     * <p>
     * The rendering uses OpenGL methods such as glBegin and glEnd for defining
     * the drawing primitives, and glVertex2f for specifying vertex positions.
     * <p>
     * This method assumes that the shape's geometry is defined by the implementation
     * of the {@code points()} method, which provides an array of vertices.
     * <p>
     * Note that this function does not handle borders or additional styling outside the shape's fill.
     */
    public void draw() {
        Vector2f[] pts = points();

        glColor4f(color.r(), color.g(), color.b(), color.a());
        glBegin(GL_TRIANGLE_FAN);

        float cx = 0f, cy = 0f;
        for (Vector2f p : pts) {
            cx += p.getX();
            cy += p.getY();
        }
        cx /= pts.length;
        cy /= pts.length;

        glVertex2f(cx, cy);
        for (Vector2f p : pts) glVertex2f(p.getX(), p.getY());
        glVertex2f(pts[0].getX(), pts[0].getY());

        glEnd();

        if (hasBorder()) {
            glColor4f(border.getColor().r(), border.getColor().g(), border.getColor().b(), border.getColor().a());
            glLineWidth(border.getThickness());
            glBegin(GL_LINE_LOOP);
            for (Vector2f p : pts) {
                glVertex2f(p.getX(), p.getY());
            }
            glEnd();
        }
    }


    /**
     * Retrieves the current color assigned to the shape.
     *
     * @return the color of the shape, which represents its fill color.
     */
    public Color getColor() {
        return color;
    }

    /**
     * Sets the color of the shape. If the provided color is null,
     * the color is set to the default value of white, represented
     * as an RGBA value of (1f, 1f, 1f, 1f).
     *
     * @param color the new color to assign to the shape. If null,
     *              the color is set to white.
     */
    public void setColor(Color color) {
        this.color = (color == null) ? new Color(1f, 1f, 1f, 1f) : color;
    }

    /**
     * Retrieves the border of the shape.
     *
     * @return the border assigned to the shape. If no border has been set,
     * the method may return null or a default border, depending on the
     * implementation.
     */
    public Border getBorder() {
        return border;
    }

    /**
     * Sets the border of the shape. The border specifies the appearance of the shape's outline,
     * including its color and thickness. If {@code null} is provided, the shape will have no border.
     *
     * @param border the border to assign to the shape, which defines its outline's color and thickness.
     *               If {@code null}, the border is removed.
     */
    public void setBorder(Border border) {
        this.border = border;
    }

    /**
     * Determines whether the shape has a defined border.
     * A shape is considered to have a border if the border is not null,
     * its thickness is greater than 0, and its color is defined.
     *
     * @return true if the shape has a border, false otherwise
     */
    public boolean hasBorder() {
        return border != null && border.getThickness() > 0f && border.getColor() != null;
    }

}
