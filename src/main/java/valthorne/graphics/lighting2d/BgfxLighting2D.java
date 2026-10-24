package valthorne.graphics.lighting2d;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import valthorne.graphics.Color;
import valthorne.graphics.GraphicsBackend;
import valthorne.graphics.GraphicsResource;
import valthorne.graphics.bgfx.BgfxBatchRenderer;
import valthorne.graphics.bgfx.BgfxGraphics;
import valthorne.graphics.bgfx.BgfxMeshRenderer;
import valthorne.graphics.bgfx.BgfxRenderTarget;
import valthorne.graphics.bgfx.BgfxResources;
import valthorne.graphics.shader.Shader;

import static org.lwjgl.bgfx.BGFX.*;

/**
 * Owns Lighting2D's backend-independent GPU resources: a polar R32F shadow atlas,
 * RGBA8 scene capture, RGBA16F illumination and compact instanced light geometry.
 * CPU visibility, shadow caching and map invalidation remain in Lighting2D. Targets
 * resize only when dimensions change. All uploads use native copied memory and
 * remain valid through deferred presentation. Close before the owning bgfx runtime.
 */
final class BgfxLighting2D implements AutoCloseable, GraphicsResource {
    private final int capacity; // Retained atlas row count.
    private final int resolution; // Retained angular samples per atlas row.

    private final BgfxBatchRenderer instances; // Owned four-vec4 light instance storage.
    private final BgfxMeshRenderer composite; // Owned fullscreen mesh layout.
    private final FloatBuffer fullscreen; // Retained six-vertex position/UV fullscreen quad.
    private short atlas = BGFX_INVALID_HANDLE; // Owned floating-point polar shadow atlas.
    private BgfxRenderTarget scene; // Owned lazily allocated scene color/depth target.
    private BgfxRenderTarget light; // Owned lazily allocated HDR illumination target.
    private boolean closed; // Whether native resources have been released.

    /**
     * Allocates the fixed-size atlas and reusable geometry resources.
     *
     * @param capacity maximum light instances and atlas rows
     * @param resolution angular samples per shadow row
     */
    BgfxLighting2D(int capacity, int resolution) {
        this.capacity = capacity;
        this.resolution = resolution;
        instances = new BgfxBatchRenderer(capacity, 64);
        composite = new BgfxMeshRenderer(2, 2);
        fullscreen = BufferUtils.createFloatBuffer(24);
        try {
            restoreGraphicsResources();
            BgfxResources.register(this);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    /**
     * Replaces capture targets after a validated dimension change.
     *
     * @param width scene pixel width
     * @param height scene pixel height
     * @param mapWidth light-map pixel width
     * @param mapHeight light-map pixel height
     */
    void resize(int width, int height, int mapWidth, int mapHeight) {
        BgfxRenderTarget nextScene = new BgfxRenderTarget(BgfxGraphics.device(), width, height, true);
        BgfxRenderTarget nextLight;
        try {
            nextLight = new BgfxRenderTarget(BgfxGraphics.device(), mapWidth, mapHeight, false, BGFX_TEXTURE_FORMAT_RGBA16F);
        } catch (RuntimeException | Error failure) {
            nextScene.close();
            throw failure;
        }
        if (scene != null) scene.close();
        if (light != null) light.close();
        scene = nextScene;
        light = nextLight;
    }

    /**
     * Begins a transparent scene capture with depth/stencil available for UI.
     */
    void beginScene() {
        BgfxGraphics.setTarget(Short.toUnsignedInt(scene.framebuffer()), scene.width(), scene.height());
        BgfxGraphics.setViewport(0, 0, scene.width(), scene.height());
        BgfxGraphics.setScissor(0, 0, 0, 0, false);
        BgfxGraphics.clear(Color.TRANSPARENT, true);
    }

    /**
     * Uploads one changed atlas row without retaining caller staging memory.
     *
     * @param row atlas row belonging to the registered light
     * @param data complete angular depth row
     */
    void uploadShadow(int row, FloatBuffer data) {
        bgfx_update_texture_2d(atlas, 0, 0, 0, row, data.remaining(), 1, bgfx_copy(data), 0xffff);
    }

    /**
     * Selects and clears the HDR illumination target before changed light draws.
     *
     * @param ambient linear ambient illumination
     */
    void beginLights(Color ambient) {
        BgfxGraphics.setTarget(Short.toUnsignedInt(light.framebuffer()), light.width(), light.height());
        BgfxGraphics.setViewport(0, 0, light.width(), light.height());
        BgfxGraphics.setScissor(0, 0, 0, 0, false);
        BgfxGraphics.clear(ambient);
    }

    /**
     * Draws visible lights with additive HDR blending in one instanced submission.
     *
     * @param records tightly packed 64-byte light records
     * @param count visible record count
     * @param shader configured lighting program
     */
    void drawLights(ByteBuffer records, int count, Shader shader) {
        BgfxGraphics.bindTexture(0, Short.toUnsignedInt(atlas));
        instances.submit(records, count, 64, shader, BgfxGraphics.textures(), 1, BGFX_STATE_WRITE_RGB | BGFX_STATE_BLEND_ADD);
    }

    /**
     * Composes captured scene and illumination into the currently restored target.
     *
     * @param shader configured composition program
     */
    void compose(Shader shader) {
        BgfxGraphics.bindTexture(0, Short.toUnsignedInt(scene.texture()));
        BgfxGraphics.bindTexture(1, Short.toUnsignedInt(light.texture()));
        shader.setUniform2f("u_lightTexel", 1f / light.width(), 1f / light.height());
        composite.submit(fullscreen, shader, BGFX_STATE_WRITE_RGB | BGFX_STATE_WRITE_A);
    }

    /**
     * Returns the borrowed HDR attachment after the first capture has allocated it.
     *
     * @return native texture index, or 65535 before allocation
     */
    int lightTexture() {
        return light == null ? Short.toUnsignedInt(BGFX_INVALID_HANDLE) : Short.toUnsignedInt(light.texture());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (scene != null) scene.close();
        if (light != null) light.close();
        BgfxResources.unregister(this);
        releaseGraphicsResources();
        instances.close();
        composite.close();
    }

    @Override
    public void releaseGraphicsResources() {
        if (atlas == BGFX_INVALID_HANDLE) return;
        BgfxGraphics.resetTextureState(Short.toUnsignedInt(atlas));
        bgfx_destroy_texture(atlas);
        atlas = BGFX_INVALID_HANDLE;
    }

    @Override
    public void restoreGraphicsResources() {
        fullscreen.clear();
        float bottom = bgfx_get_caps().originBottomLeft() ? 0 : 1;
        float top = 1 - bottom;
        fullscreen.put(new float[]{-1, -1, 0, bottom, 1, -1, 1, bottom, 1, 1, 1, top, 1, 1, 1, top, -1, 1, 0, top, -1, -1, 0, bottom}).flip();
        int maximum = bgfx_get_caps().limits().maxTextureSize();
        if (resolution > maximum || capacity > maximum || !bgfx_is_texture_valid(0, false, 1, BGFX_TEXTURE_FORMAT_R32F, BGFX_TEXTURE_NONE))
            throw new UnsupportedOperationException("The replacement device cannot restore the polar-shadow atlas");
        atlas = bgfx_create_texture_2d(resolution, capacity, false, 1, BGFX_TEXTURE_FORMAT_R32F, BGFX_SAMPLER_MIN_POINT | BGFX_SAMPLER_MAG_POINT | BGFX_SAMPLER_MIP_POINT | BGFX_SAMPLER_V_CLAMP, null, 0);
        if (atlas == BGFX_INVALID_HANDLE) {
            releaseGraphicsResources();
            throw new IllegalStateException("Could not allocate polar-shadow atlas");
        }
        BgfxGraphics.resetTextureState(Short.toUnsignedInt(atlas));
    }

}
