package valthorne.graphics.font.adaptive;

import org.lwjgl.CLongBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Face;

import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.util.freetype.FreeType.FT_Load_Sfnt_Table;

/**
 * Normalizes compressed web-font containers to the SFNT stream exposed by
 * FreeType. Shaping, coverage and MSDF then consume the same uncompressed bytes,
 * avoiding container-support differences between the bundled native libraries.
 * Conversion happens once during native font construction, never during drawing.
 */
final class OpenTypeContainer {
    /**
     * Prevents instantiation of the focused container decoder.
     */
    private OpenTypeContainer() {}

    /**
     * Recognizes WOFF and WOFF2 signatures independently of native byte order.
     * @param bytes encoded font at position zero
     * @return whether the input uses a compressed web-font container
     */
    static boolean isCompressed(ByteBuffer bytes) {
        /*
         * Inspect signature bytes directly rather than trusting a filename extension.
         */
        return bytes.remaining() >= 4 && bytes.get(0) == 'w' && bytes.get(1) == 'O' && bytes.get(2) == 'F' && (bytes.get(3) == 'F' || bytes.get(3) == '2');
    }

    /**
     * Rejects WOFF2 before native allocation because the bundled FreeType build
     * omits its Brotli decoder. Callers can convert it to an outline SFNT or WOFF.
     * @param bytes encoded source at position zero
     */
    static void validate(ByteBuffer bytes) {
        /*
         * An actionable format error is preferable to an opaque native error code.
         */
        if (isCompressed(bytes) && bytes.get(3) == '2')
            throw new IllegalArgumentException("WOFF2 decoding is unavailable in the bundled FreeType build; convert the font to TTF, OTF or WOFF before loading");
    }

    /**
     * Copies FreeType's already decoded SFNT stream into independently owned memory.
     * The caller replaces its encoded native buffer and frees this result on close.
     * @param face live FreeType face over the original compressed source
     * @return owned uncompressed TrueType/OpenType bytes
     */
    static ByteBuffer decode(FT_Face face) {
        /*
         * FreeType's zero-tag table access exposes its decompressed WOFF stream.
         * Reuse that decoder instead of
         * adding a second compression dependency or a separate shaping callback.
         */
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CLongBuffer length = stack.callocCLong(1);
            AdaptiveNativeFace.check(FT_Load_Sfnt_Table(face, 0, 0, null, length));
            long size = length.get(0);
            if (size < 12 || size > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid decoded OpenType stream size: " + size);
            ByteBuffer decoded = memAlloc((int) size);
            try {
                AdaptiveNativeFace.check(FT_Load_Sfnt_Table(face, 0, 0, decoded, length));
                if (isCompressed(decoded)) throw new IllegalArgumentException("FreeType did not expose a decoded OpenType stream");
                return decoded;
            } catch (RuntimeException | Error failure) {
                memFree(decoded);
                throw failure;
            }
        }
    }
}
