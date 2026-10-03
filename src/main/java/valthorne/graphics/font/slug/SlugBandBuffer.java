package valthorne.graphics.font.slug;

import org.lwjgl.BufferUtils;

import java.nio.IntBuffer;
import java.util.Arrays;

/**
 * CPU staging writer for two-integer band headers and curve-address texels.
 * Tracks linear texel offsets before padding rows for an RG32UI upload.
 * <p>The writer owns CPU staging values only. Generated integer buffers feed band-texture
 * uploads; texture creation and disposal belong to SlugFont rather than this helper.</p>
 *
 * @author Albert Beaupre
 */
final class SlugBandBuffer {
    private final int width; // Texture row width in texels.
    private int[] values = new int[4096]; // Growable primitive component storage for staged texels.
    private int count; // Number of populated primitive components in staging storage.

    /**
     * Creates empty integer band staging storage.
     *
     * @param width number of texels per row
     */
    SlugBandBuffer(int width) {
        this.width = width;
    }

    /**
     * Returns the next linear texel index before final row padding.
     *
     * @return number of two-component texels already staged
     */
    int position() {
        return count / 2;
    }

    /**
     * Appends one two-integer band header or curve-address texel.
     *
     * @param r first unsigned-integer bit pattern
     * @param g second unsigned-integer bit pattern
     */
    void write(int r, int g) {
        if (count + 2 > values.length) values = Arrays.copyOf(values, values.length * 2);
        values[count++] = r;
        values[count++] = g;
    }

    /**
     * Computes the rows required for staged two-integer texels, with a minimum
     * of one row even when no band data has been written.
     *
     * @return required texture height
     */
    int height() {
        int texels = Math.max(1, (count + 1) / 2);
        return Math.max(1, (texels + width - 1) / width);
    }

    /**
     * Copies staged integers into a direct buffer padded with zeros to the requested
     * row count, then flips it for upload.
     *
     * @param height row count at least the required height
     * @return upload-ready RG integer buffer
     */
    IntBuffer toBuffer(int height) {
        int capacity = width * height * 2;
        IntBuffer buffer = BufferUtils.createIntBuffer(capacity);
        buffer.put(values, 0, count);
        while (buffer.position() < capacity) {
            buffer.put(0);
        }
        buffer.flip();
        return buffer;
    }
}
