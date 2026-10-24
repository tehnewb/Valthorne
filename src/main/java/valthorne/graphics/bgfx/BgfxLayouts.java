package valthorne.graphics.bgfx;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.bgfx.BGFXVertexLayout;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.bgfx.BGFX.*;

/**
 * Rebuilds retained packed vertex declarations for a replacement renderer while
 * preserving the CPU record format. Used only during device transitions.
 */
final class BgfxLayouts {
    /**
     * Prevents constructing the vertex declaration converter.
     */
    private BgfxLayouts() {
    }

    /**
     * Re-encodes all attributes and preserves explicit padding and byte offsets.
     *
     * @param layout owned declaration to update in place
     */
    static void restore(BGFXVertexLayout layout) {
        /*
         * Decode the old declaration before overwriting it; normalized integer
         * attributes can use renderer-specific encodings even with equal strides.
         */
        try (MemoryStack stack = MemoryStack.stackPush()) {
            BGFXVertexLayout next = BGFXVertexLayout.calloc(stack);
            ByteBuffer count = stack.malloc(1);
            IntBuffer type = stack.mallocInt(1);
            ByteBuffer normalized = stack.malloc(1);
            ByteBuffer integer = stack.malloc(1);
            bgfx_vertex_layout_begin(next, bgfx_get_caps().rendererType());
            int stride = Short.toUnsignedInt(layout.stride());
            for (int offset = 0; offset < stride; ) {
                boolean found = false;
                for (int attribute = 0; attribute < BGFX_ATTRIB_COUNT; attribute++) {
                    if (!bgfx_vertex_layout_has(layout, attribute) || Short.toUnsignedInt(layout.offset().get(attribute)) != offset) continue;
                    bgfx_vertex_layout_decode(layout, attribute, count, type, normalized, integer);
                    bgfx_vertex_layout_add(next, attribute, count.get(0), type.get(0), normalized.get(0) != 0, integer.get(0) != 0);
                    offset = Short.toUnsignedInt(next.stride());
                    found = true;
                    break;
                }
                if (!found) {
                    bgfx_vertex_layout_skip(next, 1);
                    offset++;
                }
            }
            bgfx_vertex_layout_end(next);
            if (Short.toUnsignedInt(next.stride()) != stride) throw new IllegalStateException("Replacement renderer changed the packed vertex stride");
            layout.set(next);
        }
    }
}
