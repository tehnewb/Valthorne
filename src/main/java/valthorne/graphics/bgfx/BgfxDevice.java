package valthorne.graphics.bgfx;

import org.lwjgl.bgfx.BGFXCaps;
import org.lwjgl.bgfx.BGFXInit;
import org.lwjgl.bgfx.BGFXPlatformData;
import org.lwjgl.bgfx.BGFXVertexLayout;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;
import valthorne.Window;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.bgfx.BGFXPlatform.bgfx_render_frame;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWNativeCocoa.glfwGetCocoaWindow;
import static org.lwjgl.glfw.GLFWNativeGLX.glfwGetGLXContext;
import static org.lwjgl.glfw.GLFWNativeNSGL.glfwGetNSGLContext;
import static org.lwjgl.glfw.GLFWNativeWGL.glfwGetWGLContext;
import static org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window;
import static org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Display;
import static org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window;

/**
 * Owns Valthorne's single bgfx runtime and solid-color shader resources. bgfx runs
 * on the calling render thread using the borrowed GLFW OpenGL context. Flush-only
 * frames execute immediately without presenting, leaving presentation to JGL.
 * Windows, macOS and Linux/X11 use the existing desktop OpenGL backend.
 */
public final class BgfxDevice implements AutoCloseable {
    /*
     * bgfx has a process-wide runtime; render-thread confinement needs no lock.
     */
    private static BgfxDevice active;

    final BgfxGlState state = new BgfxGlState(); // Reused state preservation around native execution.
    private final Thread owner = Thread.currentThread(); // Only thread permitted to operate the runtime.
    private final long window; // Borrowed GLFW window that owns the current context.
    private BGFXVertexLayout layout; // Owned packed position/color vertex declaration.
    private short shapeProgram = BGFX_INVALID_HANDLE; // Owned streamed-triangle program.
    private short instanceProgram = BGFX_INVALID_HANDLE; // Owned instanced-mesh program.
    private short primitiveProgram = BGFX_INVALID_HANDLE; // Owned compact rectangle and sampled-circle program.
    private boolean initialized; // Whether shutdown is required after successful initialization.
    private boolean closed; // Whether the native runtime has been released.
    private boolean homogeneousDepth; // Whether bgfx expects projection depth from minus one to one.
    private boolean restoreSwapInterval; // Whether a pending bgfx resize may modify GLFW's swap interval.
    private int width; // Last bgfx backbuffer width in framebuffer pixels.
    private int height; // Last bgfx backbuffer height in framebuffer pixels.

    /**
     * Borrows the current Valthorne context and initializes one synchronous runtime.
     * Partial initialization releases owned resources and restores the GLFW context.
     *
     * @throws IllegalStateException if another runtime is active or no context is current
     */
    BgfxDevice() {
        window = Window.getAddress();
        if (active != null) throw new IllegalStateException("Only one BgfxShapeRenderer may be active");
        if (window == 0 || glfwGetCurrentContext() != window)
            throw new IllegalStateException("Create BgfxShapeRenderer with Valthorne's context current");
        state.capture();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            BGFXInit init = BGFXInit.calloc(stack);
            bgfx_init_ctor(init);
            init.type(BGFX_RENDERER_TYPE_OPENGL);
            init.fallback(false);
            configurePlatform(init.platformData());
            var framebufferWidth = stack.mallocInt(1);
            var framebufferHeight = stack.mallocInt(1);
            glfwGetFramebufferSize(window, framebufferWidth, framebufferHeight);
            width = Math.max(1, framebufferWidth.get(0));
            height = Math.max(1, framebufferHeight.get(0));
            init.resolution()
                    .width(width)
                    .height(height)
                    .reset(resetFlags());
            bgfx_render_frame(0);
            if (!bgfx_init(init)) throw new IllegalStateException("bgfx OpenGL initialization failed");
            initialized = true;
            BGFXCaps caps = bgfx_get_caps();
            if (caps == null || (caps.supported() & BGFX_CAPS_INSTANCING) == 0)
                throw new UnsupportedOperationException("bgfx shape rendering requires GPU instancing");
            homogeneousDepth = caps.homogeneousDepth();
            layout = BGFXVertexLayout.calloc();
            bgfx_vertex_layout_begin(layout, BGFX_RENDERER_TYPE_OPENGL);
            bgfx_vertex_layout_add(layout, BGFX_ATTRIB_POSITION, (byte) 3, BGFX_ATTRIB_TYPE_FLOAT, false, false);
            bgfx_vertex_layout_add(layout, BGFX_ATTRIB_COLOR0, (byte) 4, BGFX_ATTRIB_TYPE_UINT8, true, false);
            bgfx_vertex_layout_end(layout);
            shapeProgram = BgfxPrograms.create("shape.vert");
            instanceProgram = BgfxPrograms.create("instance.vert");
            primitiveProgram = BgfxPrograms.create("primitive2d.vert", "primitive2d.frag");
            bgfx_set_view_mode(0, BGFX_VIEW_MODE_SEQUENTIAL);
            bgfx_frame(BGFX_FRAME_FLUSH);
            active = this;
        } catch (RuntimeException | Error failure) {
            release();
            throw failure;
        } finally {
            glfwMakeContextCurrent(window);
            state.restore();
            glfwSwapInterval(Window.getSwapInterval().getValue());
        }
    }

    /**
     * Supplies the native window and context handles required by the OpenGL backend.
     *
     * @param platform writable initialization storage
     */
    private void configurePlatform(BGFXPlatformData platform) {
        switch (Platform.get()) {
            case WINDOWS -> platform.nwh(glfwGetWin32Window(window))
                    .context(glfwGetWGLContext(window));
            case MACOSX -> platform.nwh(glfwGetCocoaWindow(window))
                    .context(glfwGetNSGLContext(window));
            case LINUX -> {
                if (glfwGetPlatform() != GLFW_PLATFORM_X11)
                    throw new UnsupportedOperationException("bgfx OpenGL integration currently requires Linux/X11");
                platform.ndt(glfwGetX11Display())
                        .nwh(glfwGetX11Window(window))
                        .context(glfwGetGLXContext(window));
            }
        }
    }

    /**
     * Mirrors the window's swap interval; bgfx never swaps the window itself.
     *
     * @return bgfx reset flags
     */
    private int resetFlags() {
        return Window.getSwapInterval().getValue() == 0 ? BGFX_RESET_NONE : BGFX_RESET_VSYNC;
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
     * Resizes bgfx's logical backbuffer to the current framebuffer dimensions.
     *
     * @param newWidth positive framebuffer width
     * @param newHeight positive framebuffer height
     */
    void resize(int newWidth, int newHeight) {
        if (width == newWidth && height == newHeight) return;
        width = newWidth;
        height = newHeight;
        bgfx_reset(width, height, resetFlags(), BGFX_TEXTURE_FORMAT_COUNT);
        restoreSwapInterval = true;
    }

    /**
     * Executes queued commands inline without presentation and repairs resize-time
     * swap-interval changes made by bgfx's OpenGL backend.
     */
    void execute() {
        bgfx_frame(BGFX_FRAME_FLUSH);
        if (restoreSwapInterval) {
            glfwSwapInterval(Window.getSwapInterval().getValue());
            restoreSwapInterval = false;
        }
    }

    /**
     * Rejects use from another thread, after disposal, or with another context current.
     */
    void checkOwner() {
        checkThread();
        if (Window.getAddress() != window || glfwGetCurrentContext() != window)
            throw new IllegalStateException("Valthorne's original OpenGL context must be current");
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
        if (initialized) {
            if (shapeProgram != BGFX_INVALID_HANDLE) bgfx_destroy_program(shapeProgram);
            if (instanceProgram != BGFX_INVALID_HANDLE) bgfx_destroy_program(instanceProgram);
            if (primitiveProgram != BGFX_INVALID_HANDLE) bgfx_destroy_program(primitiveProgram);
            bgfx_shutdown();
            initialized = false;
        }
        if (layout != null) {
            layout.free();
            layout = null;
        }
        if (active == this) active = null;
    }

    @Override
    public void close() {
        if (closed) return;
        checkOwner();
        state.capture();
        try {
            release();
            closed = true;
        } finally {
            glfwMakeContextCurrent(window);
            state.restore();
            glfwSwapInterval(Window.getSwapInterval().getValue());
        }
    }
}
