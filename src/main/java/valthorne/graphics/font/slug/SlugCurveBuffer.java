package valthorne.graphics.font.slug;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * CPU staging writer for four-float curve texels. Each quadratic occupies two
 * adjacent texels, with row-end padding to keep the pair on the same row.
 * Address packing assumes the production width of 4096.
 *
 * @author Albert Beaupre
 */
final class SlugCurveBuffer {
    private final int width; // Texture row width in texels.
    private float[] values = new float[4096]; // Growable primitive component storage for staged texels.
    private int count; // Number of populated primitive components in staging storage.

    /**
     * Creates empty curve staging storage.
     *
     * @param width row width; address packing requires 4096
     */
    SlugCurveBuffer(int width) {
        this.width = width;
    }

    /**
     * Appends two texels containing a quadratic's three control points. Pads the
     * last texel of a row when necessary so the two-texel record stays together.
     *
     * @param curve quadratic control points
     * @return packed address of the first texel
     */
    int writeCurve(SlugCurve curve) {
        int texel = count / 4;
        if ((texel & (width - 1)) == width - 1) {
            write(0f, 0f, 0f, 0f);
            texel++;
        }

        int x = texel & (width - 1);
        int y = texel >> 12;
        write(curve.p1x(), curve.p1y(), curve.p2x(), curve.p2y());
        write(curve.p3x(), curve.p3y(), 0f, 0f);
        return SlugFont.packLocation(x, y);
    }

    /**
     * Appends one four-component texel to curve staging storage.
     *
     * @param r first component
     * @param g second component
     * @param b third component
     * @param a fourth component
     */
    void write(float r, float g, float b, float a) {
        if (count + 4 > values.length) values = Arrays.copyOf(values, values.length * 2);
        values[count++] = r;
        values[count++] = g;
        values[count++] = b;
        values[count++] = a;
    }

    /**
     * Computes the complete row count needed for staged texels, with at least one row.
     *
     * @return required texture height
     */
    int height() {
        int texels = Math.max(1, (count + 3) / 4);
        return Math.max(1, (texels + width - 1) / width);
    }

    /**
     * Copies staged floats into a direct buffer padded with zeros to the requested
     * row count, then flips it for upload.
     *
     * @param height row count at least the required height
     * @return upload-ready RGBA float buffer
     */
    FloatBuffer toBuffer(int height) {
        int capacity = width * height * 4;
        FloatBuffer buffer = BufferUtils.createFloatBuffer(capacity);
        buffer.put(values, 0, count);
        while (buffer.position() < capacity) {
            buffer.put(0f);
        }
        buffer.flip();
        return buffer;
    }
}
