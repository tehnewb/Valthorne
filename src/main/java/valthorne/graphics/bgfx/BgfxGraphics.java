package valthorne.graphics.bgfx;

import java.util.Arrays;
import org.lwjgl.nanovg.NanoVGBGFX;
import valthorne.graphics.Color;
import valthorne.graphics.GraphicsBackend;
import valthorne.graphics.shader.Shader;
import valthorne.graphics.texture.TextureFilter;

import static org.lwjgl.bgfx.BGFX.*;

/**
 * Coordinates the engine's bgfx frame, render-target and viewport state. These values are explicit inputs to sequential bgfx views. All calls belong to the application thread; no synchronization is used.
 *
 * <p>Painter-ordered sprite, Slug and NanoVG submissions share a view until the
 * render target or viewport changes. Matrices are per-draw uniforms, preventing
 * later camera changes from mutating earlier queued draws. Clear operations start
 * a fresh view so a mid-frame clear cannot erase earlier work out of order. A
 * frame supports 256 distinct target/viewport/clear passes, not 256 draw calls.</p>
 *
 * <p>JGL owns this coordinator and closes it after application resources. Resource
 * constructors require an initialized device before allocating native resources.
 * Retained resources must be disposed before the owning runtime is shut down.</p>
 */
public final class BgfxGraphics {
    /**
     * Monotonic device generation used to invalidate GPU-derived caches.
     */
    private static long generation;

    /**
     * Active JGL-owned renderer, or null outside a bgfx application.
     */
    private static BgfxDesktopWindow device;
    /**
     * Shader explicitly bound by immediate-draw callers, or null for the default.
     */
    private static Shader shader;
    /**
     * Per-texture sampler overrides indexed by native texture handle.
     */
    private static int[] samplerFlags;
    /**
     * Bottom-left physical viewport rectangle shared by the public drawing APIs.
     */
    private static final int[] viewport = new int[4];
    /**
     * Bottom-left physical scissor rectangle for shared viewport scopes.
     */
    private static final int[] scissor = new int[4];
    /**
     * Whether a physical scissor is active for the current pass.
     */
    private static boolean scissorEnabled;
    /**
     * Active framebuffer handle; invalid selects the native backbuffer.
     */
    private static short target = BGFX_INVALID_HANDLE;
    /**
     * Physical height of the active target, used to convert viewport orientation.
     */
    private static int targetHeight;
    /**
     * Physical width of the active target for negative fill-viewport cropping.
     */
    private static int targetWidth;
    /**
     * Texture-unit state used by public immediate helpers and font uploads.
     */
    private static final int[] textures = new int[16];
    /**
     * Active public texture-unit selector, used by Texture.bind.
     */
    private static int textureUnit;
    /**
     * Next available pass identifier within the current frame.
     */
    private static int nextView;
    /**
     * Next floating-point clear palette slot; index 255 is bgfx's unused sentinel.
     */
    private static int nextClear;
    /**
     * Current painter-ordered pass, or minus one when state requires a new pass.
     */
    private static int currentView = -1;
    /**
     * Monotonic frame generation used by resource upload cursors.
     */
    private static long frameId;
    /**
     * Whether a frame has begun and has not yet been presented.
     */
    private static boolean frameActive;

    /**
     * Prevents constructing the application graphics coordinator.
     */
    private BgfxGraphics() {
    }

    /**
     * Reads the device generation without performing a native query.
     *
     * @return device lifetime generation
     */
    public static long generation() {
        /*
         * A primitive generation also detects native context-address reuse.
         */
        return generation;
    }

    /**
     * Resolves a stable engine texture ID for a native binding.
     *
     * @param id engine texture ID or native texture index
     * @return native texture index
     */
    public static int textureHandle(int id) {
        /*
         * Engine IDs remain stable while their underlying native allocations change.
         */
        return BgfxTextureHandles.resolve(id);
    }

    /**
     * Completes submitted work before native resource teardown.
     */
    static void finishFrame() {
        /*
         * Initialization can queue draws before the first main-loop boundary.
         */
        if (frameActive) present();
    }

    /**
     * Reinitializes pass state after changing the device behind the same window.
     *
     * @param backend replacement renderer
     */
    static void restart(GraphicsBackend backend) {
        /*
         * Logical bindings survive; borrowed native bindings are invalidated.
         * Advance the generation before initialization so stale UI caches remain
         * invalid even if both replacement and recovery initialization fail.
         */
        generation++;
        frameActive = false;
        device.replaceBackend(backend);
        samplerFlags = new int[bgfx_get_caps().limits().maxTextures()];
        Arrays.fill(samplerFlags, -1);
        for (int i = 0; i < textures.length; i++)
            if (textures[i] < 65536) textures[i] = Short.toUnsignedInt(BGFX_INVALID_HANDLE);
        currentView = -1;
        beginFrame();
    }

    /**
     * Attaches the engine renderer to a window created with GLFW_NO_API.
     *
     * @param window JGL-owned GLFW window
     * @param vsync whether vertical synchronization is enabled
     */
    public static void initialize(long window, boolean vsync) {
        /*
         * Preserve the attachment API's original single-sample default.
         */
        initialize(window, vsync, 0, GraphicsBackend.OPENGL);
    }

    /**
     * Attaches the renderer with the window configuration's multisample policy.
     *
     * @param window JGL-owned GLFW_NO_API window
     * @param vsync whether vertical synchronization is enabled
     * @param samples requested sample count, zero through sixteen
     */
    public static void initialize(long window, boolean vsync, int samples) {
        /*
         * Existing attachment callers select the portable desktop default.
         */
        initialize(window, vsync, samples, GraphicsBackend.OPENGL);
    }

    /**
     * Attaches the requested renderer to a platform-owned native window.
     *
     * @param window GLFW_NO_API window
     * @param vsync presentation synchronization
     * @param samples multisample count
     * @param backend explicit graphics API
     */
    public static void initialize(long window, boolean vsync, int samples, GraphicsBackend backend) {
        /*
         * GLFW lifetime remains with JGL; the attached wrapper owns only bgfx.
         */
        if (device != null) throw new IllegalStateException("bgfx graphics is already initialized");
        device = new BgfxDesktopWindow(window, vsync, samples, backend);
        generation++;
        samplerFlags = new int[bgfx_get_caps().limits().maxTextures()];
        Arrays.fill(samplerFlags, -1);
        Arrays.fill(textures, Short.toUnsignedInt(BGFX_INVALID_HANDLE));
        textureUnit = 0;
        beginFrame();
    }

    /**
     * Reports whether public engine resources should use bgfx.
     *
     * @return true while JGL owns a bgfx renderer
     */
    public static boolean isActive() {
        /*
         * A single nullable runtime reference is the cold-boundary backend check.
         */
        return device != null;
    }

    /**
     * Returns the initialized runtime and validates render-thread ownership.
     *
     * @return borrowed runtime wrapper
     */
    public static BgfxDesktopWindow device() {
        /*
         * Resource operations must not reach native bgfx after application shutdown.
         */
        if (device == null) throw new IllegalStateException("No bgfx application is running");
        device.checkOwner();
        return device;
    }

    /**
     * Begins one frame without resetting commands already submitted during init.
     */
    public static void beginFrame() {
        /*
         * A generation resets upload offsets once per actual presentation cycle,
         * including resources rendered while application.init is running.
         */
        device().refreshSize();
        if (frameActive) return;
        frameActive = true;
        frameId++;
        nextView = 0;
        nextClear = 0;
        currentView = -1;
        target = BGFX_INVALID_HANDLE;
        targetHeight = device.height();
        targetWidth = device.width();
        scissorEnabled = false;
        viewport[0] = viewport[1] = 0;
        viewport[2] = device.width();
        viewport[3] = device.height();
    }

    /**
     * Presents through bgfx and closes the current upload generation.
     *
     * @return native frame number for readback completion
     */
    public static int present() {
        /*
         * Presentation is the sole point after which per-frame storage may be reused.
         */
        int frame = device().present();
        frameActive = false;
        return frame;
    }

    /**
     * Completes a synchronous backbuffer readback without presenting the window.
     * Captured output is PNG regardless of the path suffix. An empty path waits
     * for GPU completion without writing a file. Explicit target and binding state
     * is restored after the internal frame boundary; finish pending public batches
     * before requesting a capture.
     *
     * @param path output path, or an empty string for synchronization only
     */
    public static void capture(String path) {
        /*
         * Readback maps the backbuffer on the synchronous renderer, providing an
         * actual GPU completion point. Flush frames execute without swapping.
         */
        if (path == null) throw new NullPointerException("path");
        BgfxRenderState saved = new BgfxRenderState();
        saved.store();
        try {
            beginFrame();
            setTarget(BGFX_INVALID_HANDLE, device.width(), device.height());
            setViewport(0, 0, device.width(), device.height());
            setScissor(0, 0, 0, 0, false);
            // Vulkan needs a backbuffer view even when no draws preceded this request.
            bgfx_touch(view());
            long before = device.requestScreenshot(path);
            for (int attempt = 0; attempt < 4; attempt++) {
                device.flush();
                frameActive = false;
                beginFrame();
                if (device.screenshotCompleted(path, before)) return;
            }
            throw new IllegalStateException("Native screenshot did not complete after four flush frames");
        } finally {
            saved.restore();
        }
    }

    /**
     * Returns the current upload generation without allocating a frame object.
     *
     * @return monotonically increasing generation
     */
    public static long frameId() {
        /*
         * Batches compare primitive generations instead of maintaining registries.
         */
        return frameId;
    }

    /**
     * Returns a sequential view configured for the active target and viewport.
     *
     * @return pass identifier shared by compatible ordered renderers
     */
    public static int view() {
        /*
         * Views represent spatial pass state; shader matrices remain per draw.
         */
        device();
        if (!frameActive) throw new IllegalStateException("Begin the bgfx frame before drawing");
        if (currentView < 0) {
            if (nextView >= 256) throw new IllegalStateException("bgfx frame exceeded 256 render passes");
            currentView = nextView++;
            bgfx_set_view_clear(currentView, BGFX_CLEAR_NONE, 0, 1f, 0);
            bgfx_set_view_mode(currentView, BGFX_VIEW_MODE_SEQUENTIAL);
            configureView();
        }
        return currentView;
    }

    /**
     * Starts a new ordered pass before a renderer supplies custom view matrices.
     * Later managed draws must also invalidate that pass before sharing view state.
     */
    public static void invalidateView() {
        /*
         * Dedicated matrix scopes preserve queued predefined uniforms without
         * flushing or presenting the application frame.
         */
        device();
        currentView = -1;
    }

    /**
     * Reapplies target and viewport after NanoVG updates its default view rectangle.
     */
    public static void configureView() {
        /*
         * bgfx view state is resolved at frame execution; restore the caller's
         * physical viewport so NanoVG and sprite submissions share exact bounds.
         */
        if (currentView < 0) return;
        bgfx_set_view_frame_buffer(currentView, target);
        int left = Math.max(0, viewport[0]);
        int bottom = Math.max(0, viewport[1]);
        int right = (int) Math.min(targetWidth, (long) viewport[0] + viewport[2]);
        int top = (int) Math.min(targetHeight, (long) viewport[1] + viewport[3]);
        if (right <= left || top <= bottom) {
            bgfx_set_view_rect(currentView, 0, 0, 1, 1);
            bgfx_set_view_scissor(currentView, 65535, 65535, 1, 1);
            return;
        }
        bgfx_set_view_rect(currentView, left, targetHeight - top, right - left, top - bottom);
        if (scissorEnabled) {
            left = Math.max(left, scissor[0]);
            bottom = Math.max(bottom, scissor[1]);
            right = (int) Math.min(right, (long) scissor[0] + scissor[2]);
            top = (int) Math.min(top, (long) scissor[1] + scissor[3]);
            if (right <= left || top <= bottom) bgfx_set_view_scissor(currentView, 65535, 65535, 1, 1);
            else bgfx_set_view_scissor(currentView, left, targetHeight - top, right - left, top - bottom);
        } else bgfx_set_view_scissor(currentView, 0, 0, 0, 0);
    }

    /**
     * Starts a new ordered color/depth/stencil clear on the current target.
     *
     * @param color borrowed RGBA clear color
     */
    public static void clear(Color color) {
        /*
         * A fresh view preserves immediate clear ordering in the deferred renderer.
         */
        currentView = -1;
        int view = view();
        int left = scissorEnabled ? Math.max(0, scissor[0]) : 0;
        int bottom = scissorEnabled ? Math.max(0, scissor[1]) : 0;
        int right = scissorEnabled ? (int) Math.min(targetWidth, (long) scissor[0] + scissor[2]) : targetWidth;
        int top = scissorEnabled ? (int) Math.min(targetHeight, (long) scissor[1] + scissor[3]) : targetHeight;
        if (right <= left || top <= bottom) {
            currentView = -1;
            return;
        }
        if (nextClear >= 255) throw new IllegalStateException("bgfx frame exceeded 255 distinct clears");
        int palette = nextClear++;
        bgfx_set_view_rect(view, left, targetHeight - top, right - left, top - bottom);
        bgfx_set_view_scissor(view, 0, 0, 0, 0);
        bgfx_set_palette_color_rgba32f(palette, color.r(), color.g(), color.b(), color.a());
        bgfx_set_view_clear_mrt(view, BGFX_CLEAR_COLOR | BGFX_CLEAR_DEPTH | BGFX_CLEAR_STENCIL, 1f, 0, (byte) palette, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255);
        bgfx_touch(view);
        currentView = -1;
    }

    /**
     * Computes the clip-space scale/offset preserving oversized or negative viewports.
     *
     * @param destination four floats receiving XY scale followed by XY translation
     */
    public static void viewportAdjustment(float[] destination) {
        /*
         * Native view origins are unsigned. Crop the hardware viewport and remap
         * clip coordinates so FillViewport retains the same world-to-pixel mapping.
         */
        int left = Math.max(0, viewport[0]);
        int bottom = Math.max(0, viewport[1]);
        int right = (int) Math.min(targetWidth, (long) viewport[0] + viewport[2]);
        int top = (int) Math.min(targetHeight, (long) viewport[1] + viewport[3]);
        int width = Math.max(1, right - left);
        int height = Math.max(1, top - bottom);
        destination[0] = (float) viewport[2] / width;
        destination[1] = (float) viewport[3] / height;
        destination[2] = ((float) viewport[0] - left) * 2 / width + destination[0] - 1;
        destination[3] = ((float) viewport[1] - bottom) * 2 / height + destination[1] - 1;
    }

    /**
     * Converts a logical UI width to the clipped NanoVG frame width.
     *
     * @param width original logical viewport width
     * @return positive logical frame width
     */
    public static float nanoWidth(float width) {
        /*
         * NanoVG normalizes positions using its frame size, so cropping must retain
         * the original physical pixels per logical unit.
         */
        long visible = Math.min(targetWidth, (long) viewport[0] + viewport[2]) - Math.max(0, viewport[0]);
        return Math.max(1, visible) * width / Math.max(1, viewport[2]);
    }

    /**
     * Converts a logical UI height to the clipped NanoVG frame height.
     *
     * @param height original logical viewport height
     * @return positive logical frame height
     */
    public static float nanoHeight(float height) {
        /*
         * Height uses the same crop ratio independently for stretched viewports.
         */
        long visible = Math.min(targetHeight, (long) viewport[1] + viewport[3]) - Math.max(0, viewport[1]);
        return Math.max(1, visible) * height / Math.max(1, viewport[3]);
    }

    /**
     * Returns NanoVG's horizontal logical translation after viewport cropping.
     *
     * @param width original logical UI width
     * @return translation applied before camera scaling
     */
    public static float nanoOffsetX(float width) {
        /*
         * Preserve the original left edge while the native viewport begins at zero.
         */
        return Math.min(0, viewport[0]) * width / Math.max(1, viewport[2]);
    }

    /**
     * Returns NanoVG's downward logical translation after top-edge cropping.
     *
     * @param height original logical UI height
     * @return translation applied before camera scaling
     */
    public static float nanoOffsetY(float height) {
        /*
         * NanoVG starts at the top edge, opposite the public bottom-left convention.
         */
        return -Math.max(0L, (long) viewport[1] + viewport[3] - targetHeight) * height / Math.max(1, viewport[3]);
    }

    /**
     * Copies the bottom-left physical viewport to caller storage.
     *
     * @param destination at least four integers
     */
    public static void getViewport(int[] destination) {
        /*
         * Reusable caller arrays preserve the existing viewport scope API.
         */
        device();
        System.arraycopy(viewport, 0, destination, 0, 4);
    }

    /**
     * Changes spatial pass state without mutating previously submitted view state.
     *
     * @param x physical left edge
     * @param y physical bottom edge
     * @param width nonnegative physical width
     * @param height nonnegative physical height
     */
    public static void setViewport(int x, int y, int width, int height) {
        /*
         * Zero-area viewports remain empty. Hardware rectangles are safely cropped
         * when their origin or extent extends outside the active target.
         */
        device();
        if (width < 0 || height < 0) throw new IllegalArgumentException("Viewport dimensions cannot be negative");
        if (viewport[0] == x && viewport[1] == y && viewport[2] == width && viewport[3] == height) return;
        viewport[0] = x;
        viewport[1] = y;
        viewport[2] = width;
        viewport[3] = height;
        currentView = -1;
    }

    /**
     * Reports explicit physical scissor state.
     *
     * @return current clipping enablement
     */
    public static boolean isScissorEnabled() {
        /*
         * The coordinator is the authoritative state source on the bgfx path.
         */
        return scissorEnabled;
    }

    /**
     * Copies the current physical scissor to caller-owned storage.
     *
     * @param destination four-integer destination
     */
    public static void getScissor(int[] destination) {
        /*
         * Nested viewport scopes reuse their existing primitive arrays.
         */
        System.arraycopy(scissor, 0, destination, 0, 4);
    }

    /**
     * Starts a new pass for a changed physical clipping scope.
     *
     * @param x left edge
     * @param y bottom edge
     * @param width width
     * @param height height
     * @param enabled whether clipping is enabled
     */
    public static void setScissor(int x, int y, int width, int height, boolean enabled) {
        /*
         * Scissor is per-view state, so changing it must not rewrite earlier draws.
         */
        device();
        scissor[0] = x;
        scissor[1] = y;
        scissor[2] = width;
        scissor[3] = height;
        scissorEnabled = enabled;
        currentView = -1;
    }
    /**
     * Returns the active framebuffer without transferring resource ownership.
     *
     * @return unsigned bgfx handle, or 65535 for the backbuffer
     */
    public static int target() {
        /*
         * Int handles keep the public FrameBuffer API free of signed-short sentinels.
         */
        return Short.toUnsignedInt(target);
    }

    /**
     * Returns the physical height needed to restore target orientation.
     *
     * @return target height in pixels
     */
    public static int targetHeight() {
        /*
         * The height accompanies saved framebuffer state in nested target scopes.
         */
        return targetHeight;
    }

    /**
     * Returns the active target's physical width for restoring nested target scopes.
     *
     * @return target width in pixels
     */
    public static int targetWidth() {
        /*
         * Both target dimensions are needed when a fill viewport extends offscreen.
         */
        return targetWidth;
    }

    /**
     * Selects a render target; callers must flush pending batches before switching.
     *
     * @param framebuffer unsigned handle or 65535 for the backbuffer
     * @param width physical target width
     * @param height physical target height
     */
    public static void setTarget(int framebuffer, int width, int height) {
        /*
         * Target switches create new passes even when a previous target is restored,
         * preserving dependencies across nested offscreen operations.
         */
        device();
        target = (short) framebuffer;
        targetWidth = width;
        targetHeight = height;
        currentView = -1;
    }

    /**
     * Routes a NanoVG segment into the same painter-ordered view as sprites.
     *
     * @param context valid NanoVG bgfx context
     */
    public static void selectNano(long context) {
        /*
         * NanoVG supplies its own uniforms and transient geometry, while view
         * ordering and framebuffer ownership remain coordinated by the engine.
         */
        NanoVGBGFX.nvgSetViewId(context, view());
    }

    /**
     * Stores a sampled texture without introducing implicit native state.
     *
     * @param unit texture slot from zero through fifteen
     * @param handle unsigned bgfx texture handle
     */
    public static void bindTexture(int unit, int handle) {
        /*
         * Binding is resolved when a draw is submitted, matching the public
         * texture-unit API without issuing OpenGL calls.
         */
        device();
        if (unit < 0 || unit >= textures.length) throw new IllegalArgumentException("Texture unit outside [0, 15]");
        textures[unit] = handle;
    }

    /**
     * Borrows the current texture-unit table for native draw submission.
     *
     * @return render-thread-confined texture handles
     */
    public static int[] textures() {
        /*
         * Engine renderers consume this stable array without making frame copies.
         */
        return textures;
    }

    /**
     * Selects the public texture unit used by subsequent Texture.bind calls.
     *
     * @param unit texture slot from zero through fifteen
     */
    public static void textureUnit(int unit) {
        /*
         * The selector affects managed bindings only, matching the legacy API
         * without an implicit OpenGL active-texture operation.
         */
        device();
        if (unit < 0 || unit >= textures.length) throw new IllegalArgumentException("Texture unit outside [0, 15]");
        textureUnit = unit;
    }

    /**
     * Reads the currently selected public texture unit.
     *
     * @return active slot from zero through fifteen
     */
    public static int textureUnit() {
        /*
         * Immediate render scopes save the primitive selector with their bindings.
         */
        return textureUnit;
    }
    /**
     * Updates a sampled texture's filtering policy for subsequent submissions.
     *
     * @param handle unsigned texture handle
     * @param filter selected minification/magnification policy
     */
    public static void setTextureFilter(int handle, TextureFilter filter) {
        handle = textureHandle(handle);
        /*
         * bgfx sampler state belongs to draw submission; a compact indexed table
         * preserves the Texture API without recreating immutable native textures.
         */
        device();
        int flags = (int) (BGFX_SAMPLER_U_CLAMP | BGFX_SAMPLER_V_CLAMP);
        if (filter == TextureFilter.NEAREST || filter == TextureFilter.NEAREST_MIPMAP_NEAREST || filter == TextureFilter.NEAREST_MIPMAP_LINEAR)
            flags |= (int) (BGFX_SAMPLER_MIN_POINT | BGFX_SAMPLER_MAG_POINT);
        if (filter == TextureFilter.NEAREST_MIPMAP_NEAREST || filter == TextureFilter.LINEAR_MIPMAP_NEAREST)
            flags |= (int) BGFX_SAMPLER_MIP_POINT;
        samplerFlags[handle] = flags;
    }

    /**
     * Returns a texture's sampler override for the next native draw.
     *
     * @param handle unsigned texture handle
     * @return sampler flags, or minus one for the texture's creation defaults
     */
    public static int textureFlags(int handle) {
        handle = textureHandle(handle);
        /*
         * Native handles are dense bounded indices, avoiding boxed map lookups.
         */
        return handle >= 0 && handle < samplerFlags.length ? samplerFlags[handle] : -1;
    }

    /**
     * Clears sampler overrides and bindings when a native texture slot is reused.
     *
     * @param handle allocated or released native texture index
     */
    public static void resetTextureState(int handle) {
        /*
         * Native handles recycle; a new image must not inherit a previous image's
         * filters or leave a released resource bound in the immediate API.
         */
        device();
        samplerFlags[handle] = -1;
        resetTextureBinding(handle);
    }

    /**
     * Removes a released logical or native texture from immediate bindings.
     *
     * @param id texture identifier being disposed
     */
    static void resetTextureBinding(int id) {
        /*
         * Released logical slots must not remain bound when the ID table recycles
         * them for another asset. Device-only release preserves logical bindings.
         */
        for (int i = 0; i < textures.length; i++)
            if (textures[i] == id) textures[i] = Short.toUnsignedInt(BGFX_INVALID_HANDLE);
    }
    /**
     * Selects retained shader state for immediate helpers.
     *
     * @param selected compiled shader, or null for the default
     */
    public static void bindShader(Shader selected) {
        /*
         * Binding changes managed state only; numeric uniforms are replayed when
         * geometry is submitted to avoid deferred-state leakage.
         */
        device();
        shader = selected;
    }

    /**
     * Returns the shader selected by the public immediate drawing API.
     *
     * @return borrowed shader, or null
     */
    public static Shader shader() {
        /*
         * A primitive draw can use the normal batch shader when no override exists.
         */
        return shader;
    }
    /**
     * Shuts down bgfx after all engine/application GPU resources have been closed.
     */
    public static void dispose() {
        /*
         * Clear managed references even when native shutdown throws so completed
         * applications cannot leave an apparently active renderer behind.
         */
        if (device == null) return;
        try {
            device.close();
        } finally {
            device = null;
            shader = null;
            samplerFlags = null;
            BgfxResources.clear();
            frameActive = false;
            currentView = -1;
        }
    }
}
