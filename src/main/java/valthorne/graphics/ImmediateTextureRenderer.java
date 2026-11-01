package valthorne.graphics;

import java.nio.FloatBuffer;
import valthorne.Window;
import valthorne.graphics.bgfx.BgfxBatchRenderer;
import valthorne.graphics.bgfx.BgfxGraphics;
import valthorne.graphics.shader.Shader;
import valthorne.graphics.shader.TexturedQuadShader;

/**
 * Shares compact immediate-quad geometry across sprites and framebuffer blits.
 * Program and instance storage allocate lazily and belong to the engine runtime.
 * Positions and UVs are read by absolute index and remain caller-owned.
 */
public final class ImmediateTextureRenderer {
    /**
     * Owned lazily created instance storage.
     */
    private static BgfxBatchRenderer bgfxRenderer;
    /**
     * Owned fallback quad program.
     */
    private static Shader bgfxDefaultShader;

    /**
     * Prevents constructing the shared immediate renderer.
     */
    private ImmediateTextureRenderer() {
    }

    /**
     * Releases shared resources before graphics runtime shutdown.
     */
    public static void dispose() {
        /*
         * Release only lazily acquired resources; subsequent drawing can reinitialize.
         */
        if (bgfxRenderer == null) return;
        bgfxRenderer.close();
        bgfxDefaultShader.dispose();
        bgfxRenderer = null;
        bgfxDefaultShader = null;
    }

    /**
     * Draws copied four-corner quads using the selected program or the default.
     *
     * @param textureID borrowed texture handle
     * @param positions four XY corners per quad
     * @param uvs corresponding UV pairs
     * @param quadCount populated quad count
     * @param color tint, or null for white
     */
    public static void drawQuads(int textureID, FloatBuffer positions, FloatBuffer uvs, int quadCount, Color color) {
        /*
         * All APIs use the same compact instance record and retained shader values.
         */
        if (quadCount <= 0) return;
        if (positions == null || uvs == null) throw new NullPointerException("Quad positions and UVs are required");
        if (bgfxRenderer == null) {
            BgfxBatchRenderer renderer = new BgfxBatchRenderer(256);
            try {
                bgfxDefaultShader = new TexturedQuadShader();
                bgfxRenderer = renderer;
            } catch (RuntimeException | Error failure) {
                renderer.close();
                throw failure;
            }
        }
        Shader shader = BgfxGraphics.shader();
        if (shader == null) shader = bgfxDefaultShader;
        shader.setUniformMatrix4("u_mvp", Window.getProjectionMatrix());
        shader.setUniform1i("u_texture", 0);
        BgfxGraphics.bindTexture(0, textureID);
        bgfxRenderer.quads(positions, uvs, quadCount, color, shader, BgfxGraphics.textures());
    }
}
