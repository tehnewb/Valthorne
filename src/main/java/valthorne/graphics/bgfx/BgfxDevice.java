package valthorne.graphics.bgfx;

import org.lwjgl.bgfx.BGFXCaps;
import org.lwjgl.bgfx.BGFXVertexLayout;
import valthorne.graphics.GraphicsResource;

import static org.lwjgl.bgfx.BGFX.*;

/**
 * Owns shape shader programs and vertex layouts on JGL's active bgfx device.
 * Submissions remain queued until engine presentation. Creation and disposal belong
 * to the rendering thread; close dependent meshes before this resource set. Closing
 * releases only owned graphics resources and leaves the engine device running.
 */
public final class BgfxDevice implements AutoCloseable, GraphicsResource {
    private final Thread owner = Thread.currentThread(); // Only thread permitted to operate the runtime.
    private BGFXVertexLayout layout; // Owned packed position/color vertex declaration.
    private short shapeProgram = BGFX_INVALID_HANDLE; // Owned streamed-triangle program.
    private short instanceProgram = BGFX_INVALID_HANDLE; // Owned instanced-mesh program.
    private short primitiveProgram = BGFX_INVALID_HANDLE; // Owned compact rectangle and sampled-circle program.
    private boolean closed; // Whether the native runtime has been released.
    private boolean homogeneousDepth; // Whether bgfx expects projection depth from minus one to one.

    /**
     * Allocates shape programs and layouts on the current engine device. Failed
     * initialization releases partially created resources.
     *
     * @throws IllegalStateException if no engine graphics device is initialized
     */
    BgfxDevice() {
        BgfxGraphics.device();
        try {
            initializeResources();
            BgfxResources.register(this);
        } catch (RuntimeException | Error failure) {
            release();
            throw failure;
        }
    }

    /**
     * Creates owned shape programs and layouts within the initialized runtime.
     */
    private void initializeResources() {
        BGFXCaps caps = bgfx_get_caps();
        if (caps == null || (caps.supported() & BGFX_CAPS_INSTANCING) == 0)
            throw new UnsupportedOperationException("bgfx shape rendering requires GPU instancing");
        homogeneousDepth = caps.homogeneousDepth();
        layout = BGFXVertexLayout.calloc();
        bgfx_vertex_layout_begin(layout, rendererType());
        bgfx_vertex_layout_add(layout, BGFX_ATTRIB_POSITION, (byte) 3, BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_add(layout, BGFX_ATTRIB_COLOR0, (byte) 4, BGFX_ATTRIB_TYPE_UINT8, true, false);
        bgfx_vertex_layout_end(layout);
        shapeProgram = BgfxPrograms.create("shape.vert");
        instanceProgram = BgfxPrograms.create("instance.vert");
        primitiveProgram = BgfxPrograms.create("primitive2d.vert", "primitive2d.frag");

    }

    /**
     * Returns the selected renderer for compatible native layout construction.
     *
     * @return bgfx renderer type
     */
    int rendererType() {
        return bgfx_get_caps().rendererType();
    }

    /**
     * Returns the immutable declaration used by streamed and cached mesh vertices.
     *
     * @return borrowed native declaration
     */
    public BGFXVertexLayout layout() {
        return layout;
    }

    /**
     * Selects the program matching the requested submission path.
     *
     * @param instances whether instance attributes are supplied
     * @return borrowed program handle
     */
    short program(boolean instances) {
        return instances ? instanceProgram : shapeProgram;
    }

    /**
     * Returns the compact two-vec4 2D instance program.
     *
     * @return borrowed program handle
     */
    short primitiveProgram() {
        return primitiveProgram;
    }

    /**
     * Returns the projection convention queried once during native initialization.
     *
     * @return whether projection clip depth ranges from minus one to one
     */
    boolean homogeneousDepth() {
        return homogeneousDepth;
    }

    /**
     * Rejects use from another thread, after disposal, or with another context current.
     */
    void checkOwner() {
        checkThread();
        BgfxGraphics.device();
    }

    /**
     * Checks Java lifetime and thread confinement without a native context query.
     * CPU-only submissions use this check; begin and end also validate the context.
     */
    void checkThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Use bgfx on its creating render thread");
        if (closed) throw new IllegalStateException("BgfxShapeRenderer is closed");
    }

    /**
     * Releases initialized native resources during normal close or constructor failure.
     */
    private void release() {
        if (shapeProgram != BGFX_INVALID_HANDLE) bgfx_destroy_program(shapeProgram);
        if (instanceProgram != BGFX_INVALID_HANDLE) bgfx_destroy_program(instanceProgram);
        if (primitiveProgram != BGFX_INVALID_HANDLE) bgfx_destroy_program(primitiveProgram);
        shapeProgram = instanceProgram = primitiveProgram = BGFX_INVALID_HANDLE;
        if (layout != null) {
            layout.free();
            layout = null;
        }
    }

    @Override
    public void close() {
        if (closed) return;
        checkOwner();
        BgfxResources.unregister(this);
        release();
        closed = true;
    }

    @Override
    public void releaseGraphicsResources() {
        release();
    }

    @Override
    public void restoreGraphicsResources() {
        try {
            initializeResources();
        } catch (RuntimeException | Error failure) {
            release();
            throw failure;
        }
    }

}
