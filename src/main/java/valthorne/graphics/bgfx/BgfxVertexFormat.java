package valthorne.graphics.bgfx;

import java.nio.ByteBuffer;
import valthorne.graphics.Color;

/**
 * Encodes the shared sixteen-byte position/color vertex format without temporary
 * vectors or per-vertex allocation. Buffers remain owned by the caller.
 */
public final class BgfxVertexFormat {
    /**
     * Prevents construction of this stateless vertex encoder.
     */
    private BgfxVertexFormat() {}

    /**
     * Encodes Valthorne's byte colors for normalized RGBA vertex attributes.
     *
     * @param color nonnull tint
     * @return little-endian RGBA bytes packed in one integer
     */
    public static int pack(Color color) {
        /*
         * Native desktop targets are little-endian, so red occupies the low byte.
         */
        return color.getRed() | (color.getGreen() << 8) | (color.getBlue() << 16) | (color.getAlpha() << 24);
    }

    /**
     * Appends one position and packed tint with no temporary vectors.
     *
     * @param data destination with at least sixteen writable bytes
     * @param x local X
     * @param y local Y
     * @param z local Z
     * @param tint packed RGBA bytes
     */
    public static void vertex(ByteBuffer data, float x, float y, float z, int tint) {
        /*
         * A sixteen-byte vertex keeps solid shapes compact and naturally aligned.
         */
        data.putFloat(x)
                .putFloat(y)
                .putFloat(z)
                .putInt(tint);
    }
}
