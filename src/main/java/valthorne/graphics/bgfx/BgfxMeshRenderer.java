package valthorne.graphics.bgfx;

import java.nio.FloatBuffer;
import org.lwjgl.bgfx.BGFXTransientVertexBuffer;
import org.lwjgl.bgfx.BGFXVertexLayout;
import org.lwjgl.system.MemoryStack;
import valthorne.graphics.GraphicsResource;
import valthorne.graphics.shader.Shader;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Submits small procedural meshes using bgfx's frame-owned transient storage.
 * The position attribute is followed by zero or more texture-coordinate attributes;
 * all components are floats. The layout is retained, while each submission receives
 * a distinct native range that remains valid until presentation. No Java staging
 * objects or GPU resources are created per draw. Calls require the rendering thread.
 * Exhausted transient storage fails explicitly rather than dropping geometry.
 */
public final class BgfxMeshRenderer implements AutoCloseable, GraphicsResource {
    private final BGFXVertexLayout layout; // Owned immutable vertex description.
    private final int floatsPerVertex; // Packed float count validated once at construction.
    private boolean closed; // Whether native layout storage has been freed.

    /**
     * Creates a tightly packed floating-point vertex layout.
     *
     * @param components position width followed by up to eight texture-coordinate widths
     */
    public BgfxMeshRenderer(int... components) {
        BgfxGraphics.device();
        if (components.length < 1 || components.length > 9)
            throw new IllegalArgumentException("A mesh requires position and up to eight UV attributes");
        int floats = 0;
        for (int size : components) {
            if (size < 1 || size > 4) throw new IllegalArgumentException("Attribute widths must be between one and four");
            floats += size;
        }
        floatsPerVertex = floats;
        layout = BGFXVertexLayout.calloc();
        bgfx_vertex_layout_begin(layout, BgfxGraphics.device().backend().rendererType());
        for (int i = 0; i < components.length; i++)
            bgfx_vertex_layout_add(layout, i == 0 ? BGFX_ATTRIB_POSITION : BGFX_ATTRIB_TEXCOORD0 + i - 1, (byte) components[i], BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_end(layout);
        BgfxResources.register(this);
    }

    /**
     * Copies one packed mesh into frame-owned memory and submits its explicit state.
     *
     * @param vertices packed vertices beginning at the current buffer position
     * @param shader compatible compiled program with retained uniforms
     * @param state complete native write, blend, topology and depth flags
     */
    public void submit(FloatBuffer vertices, Shader shader, long state) {
        BgfxGraphics.device();
        if (closed) throw new IllegalStateException("Mesh renderer is closed");
        if (vertices.remaining() % floatsPerVertex != 0)
            throw new IllegalArgumentException("Incomplete mesh vertex");
        int count = vertices.remaining() / floatsPerVertex;
        if (count == 0) return;
        if (bgfx_get_avail_transient_vertex_buffer(count, layout) != count)
            throw new IllegalStateException("bgfx transient geometry capacity exceeded for this frame");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long buffer = stack.ncalloc(BGFXTransientVertexBuffer.ALIGNOF, 1, BGFXTransientVertexBuffer.SIZEOF);
            nbgfx_alloc_transient_vertex_buffer(buffer, count, layout.address());
            memCopy(memAddress(vertices), memGetAddress(buffer + BGFXTransientVertexBuffer.DATA), (long) vertices.remaining() * Float.BYTES);
            shader.bgfx().apply(BgfxGraphics.textures(), 16);
            nbgfx_set_transient_vertex_buffer((byte) 0, buffer, 0, count);
            bgfx_set_state(state, 0);
            bgfx_submit(BgfxGraphics.view(), shader.bgfx().handle(), 0, BGFX_DISCARD_ALL);
        }
    }

    @Override
    public void close() {
        BgfxGraphics.device();
        if (closed) return;
        BgfxResources.unregister(this);
        closed = true;
        layout.free();
    }

    @Override
    public void restoreGraphicsResources() {
        BgfxLayouts.restore(layout);
    }

}
