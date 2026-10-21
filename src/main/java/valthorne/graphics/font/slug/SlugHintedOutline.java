package valthorne.graphics.font.slug;

import org.lwjgl.util.freetype.FT_Outline;
import org.lwjgl.util.freetype.FT_Outline_ConicToFunc;
import org.lwjgl.util.freetype.FT_Outline_CubicToFunc;
import org.lwjgl.util.freetype.FT_Outline_Funcs;
import org.lwjgl.util.freetype.FT_Outline_LineToFunc;
import org.lwjgl.util.freetype.FT_Outline_MoveToFunc;
import org.lwjgl.util.freetype.FT_Vector;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.util.freetype.FreeType.FT_Outline_Decompose;

/**
 * Reusable native outline callbacks for one thread-confined FreeType face. They
 * convert hinted 26.6-pixel points back into em-space Slug curves on a geometry
 * cache miss. Native callbacks and their function table are allocated once and
 * released with the face; ordinary retained text drawing never invokes them.
 */
final class SlugHintedOutline implements AutoCloseable {
    private final FT_Outline_MoveToFunc move = FT_Outline_MoveToFunc.create((to, user) -> move(to)); // Owned contour-start callback.
    private final FT_Outline_LineToFunc line = FT_Outline_LineToFunc.create((to, user) -> line(to)); // Owned straight-segment callback.
    private final FT_Outline_ConicToFunc conic = FT_Outline_ConicToFunc.create((control, to, user) -> conic(control, to)); // Owned quadratic callback.
    private final FT_Outline_CubicToFunc cubic = FT_Outline_CubicToFunc.create((first, second, to, user) -> cubic(first, second, to)); // Owned CFF cubic callback.
    private final FT_Outline_Funcs functions = FT_Outline_Funcs.calloc()
            .move_to(move)
            .line_to(line)
            .conic_to(conic)
            .cubic_to(cubic); // Owned native callback table.
    private List<SlugCurve> curves; // Current cold-path destination; null outside decomposition.
    private float scale; // Conversion from hinted 26.6 pixels to normalized em coordinates.
    private float x; // Last contour point X in em coordinates.
    private float y; // Last contour point Y in em coordinates.
    private float startX; // Current contour start X.
    private float startY; // Current contour start Y.
    private boolean contour; // Whether a contour needs explicit closure.

    /**
     * Allocates reusable native outline callbacks.
     */
    SlugHintedOutline() {}

    /**
     * Copies a hinted outline into Slug curve records.
     *
     * @param outline borrowed loaded glyph outline
     * @param pixelsPerEm physical em size used by FreeType
     * @return independent normalized curves
     */
    List<SlugCurve> load(FT_Outline outline, float pixelsPerEm) {
        curves = new ArrayList<>();
        scale = 1 / (pixelsPerEm * 64);
        contour = false;
        try {
            int status = FT_Outline_Decompose(outline, functions, 0);
            if (status != 0) throw new IllegalStateException("Cannot decompose hinted glyph: " + status);
            closeContour();
            return curves;
        } finally {
            curves = null;
        }
    }

    /**
     * Closes a preceding contour and starts the next one.
     *
     * @param address borrowed FreeType vector
     * @return native success code
     */
    private int move(long address) {
        closeContour();
        FT_Vector vector = FT_Vector.create(address);
        x = startX = vector.x() * scale;
        y = startY = vector.y() * scale;
        contour = true;
        return 0;
    }

    /**
     * Appends a straight contour edge.
     *
     * @param address borrowed destination vector
     * @return native success code
     */
    private int line(long address) {
        FT_Vector vector = FT_Vector.create(address);
        float nextX = vector.x() * scale;
        float nextY = vector.y() * scale;
        SlugCurves.line(curves, x, y, nextX, nextY);
        x = nextX;
        y = nextY;
        return 0;
    }

    /**
     * Appends an exact quadratic contour edge.
     *
     * @param controlAddress borrowed control vector
     * @param address borrowed destination vector
     * @return native success code
     */
    private int conic(long controlAddress, long address) {
        FT_Vector control = FT_Vector.create(controlAddress);
        FT_Vector vector = FT_Vector.create(address);
        float nextX = vector.x() * scale;
        float nextY = vector.y() * scale;
        SlugCurves.quadratic(curves, x, y, control.x() * scale, control.y() * scale, nextX, nextY);
        x = nextX;
        y = nextY;
        return 0;
    }

    /**
     * Appends a bounded-error quadratic approximation of a CFF cubic edge.
     *
     * @param firstAddress first borrowed control
     * @param secondAddress second borrowed control
     * @param address borrowed destination
     * @return native success code
     */
    private int cubic(long firstAddress, long secondAddress, long address) {
        FT_Vector first = FT_Vector.create(firstAddress);
        FT_Vector second = FT_Vector.create(secondAddress);
        FT_Vector vector = FT_Vector.create(address);
        float nextX = vector.x() * scale;
        float nextY = vector.y() * scale;
        SlugCurves.cubic(curves, x, y, first.x() * scale, first.y() * scale, second.x() * scale, second.y() * scale, nextX, nextY);
        x = nextX;
        y = nextY;
        return 0;
    }

    /**
     * Adds the final closing edge without removing nonempty closed curves.
     */
    private void closeContour() {
        if (contour) SlugCurves.line(curves, x, y, startX, startY);
        contour = false;
    }

    @Override
    public void close() {
        functions.free();
        cubic.free();
        conic.free();
        line.free();
        move.free();
    }
}
