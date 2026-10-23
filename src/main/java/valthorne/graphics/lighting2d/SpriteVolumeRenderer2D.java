package valthorne.graphics.lighting2d;

import org.lwjgl.BufferUtils;
import valthorne.Window;
import valthorne.graphics.bgfx.BgfxGraphics;
import valthorne.graphics.bgfx.BgfxMeshRenderer;
import valthorne.graphics.bgfx.BgfxRenderState;

import static org.lwjgl.bgfx.BGFX.*;
import valthorne.graphics.shader.Shader;
import valthorne.graphics.texture.TextureBatch;

import java.nio.FloatBuffer;

/**
 * Stylized billboard volume shading, without generated normal textures.
 * Uses authored foot/top anchors and a procedural rounded surface, not reconstructed geometry.
 * Call in painter order inside the scene capture. Borrows sprites; owns only GL resources.
 * Honors sprite transforms/UVs, but not TextureBatch-local clipping or translation.
 */
public final class SpriteVolumeRenderer2D implements AutoCloseable {
    private final BgfxMeshRenderer bgfx; // Owned native mesh path.
    private final BgfxRenderState bgfxState = new BgfxRenderState(); // Reused external drawing scope.
    private final Shader shader; // Owned shader for this rendering pass.
    private final FloatBuffer vertices = BufferUtils.createFloatBuffer(24); // Reusable world/UV/card quad staging.
    private boolean closed; // Whether the owned GL resources were released.
    /**
     * Cached position/radius uniform names for the five supported light slots.
     */
    private static final String[] LIGHT_NAMES = {"u_lights[0]", "u_lights[1]", "u_lights[2]", "u_lights[3]", "u_lights[4]"};
    /**
     * Cached intensity uniform names paired with the position/radius slots.
     */
    private static final String[] ENERGY_NAMES = {"u_energy[0]", "u_energy[1]", "u_energy[2]", "u_energy[3]", "u_energy[4]"};

    /**
     * Creates the shader and streaming quad resources on the current graphics device,
     * restoring the caller's selected bindings afterward. Close on the same
     * graphics thread after the final pass.
     */
    public SpriteVolumeRenderer2D() {
        shader = new Shader("bgfx:engine/lighting/sprite-volume.vert", "bgfx:engine/lighting/sprite-volume.frag");
        bgfx = new BgfxMeshRenderer(2, 2, 2);
    }

    /**
     * Shades one borrowed sprite card using a dominant light and up to four fills.
     * Flushes the active batch, draws one quad, and restores selected engine render state.
     *
     * @param batch active scene batch
     * @param card borrowed sprite card and authored volume anchors
     * @param overhead borrowed dominant light
     * @param fills supplementary lights, or null for none
     * @param opacity finite opacity between zero and one
     * @throws IllegalArgumentException if opacity is outside its valid range
     * @throws IllegalStateException if the renderer has been closed
     */
    public void draw(TextureBatch batch, SpriteGroundShadow2D card, PointLight2D overhead, PointLight2D[] fills, float opacity) {
        if (closed) throw new IllegalStateException("Volume renderer is closed");
        PointLight2D.nonnegative(opacity);
        if (opacity > 1) throw new IllegalArgumentException("Opacity exceeds one");
        var sprite = card.sprite;
        var quad = sprite.getVertexBuffer();
        var uv = sprite.getUVBuffer();
        float baseX = quad.get(0) + card.anchorU * (quad.get(2) - quad.get(0)) + card.anchorV * (quad.get(6) - quad.get(0));
        float baseY = quad.get(1) + card.anchorU * (quad.get(3) - quad.get(1)) + card.anchorV * (quad.get(7) - quad.get(1));
        batch.flush();

        bgfxState.store();
        try {
            BgfxGraphics.bindTexture(2, sprite.getTexture().getTextureID());
            shader.bind();
            shader.setUniform1i("u_texture", 2);
            shader.setUniformMatrix4("u_projection", Window.getProjectionMatrix());
            shader.setUniform4f("u_card", card.anchorU, card.anchorV, card.topV, card.height);
            shader.setUniform2f("u_base", baseX, baseY);
            shader.setUniform4f("u_tint", sprite.getColor().r(), sprite.getColor().g(), sprite.getColor().b(), sprite.getColor().a() * opacity);
            setLight(0, overhead);
            int count = 1;
            if (fills != null) for (PointLight2D light : fills) {
                if (count == 5) break;
                setLight(count++, light);
            }
            shader.setUniform1i("u_count", count);
            vertices.clear();
            // Sprite vertex buffers are perimeter ordered; triangle strips need 0,1,3,2.
            for (int c = 0; c < 4; c++) {
                int index = c < 2 ? c : 5 - c;
                vertices.put(quad.get(index * 2)).put(quad.get(index * 2 + 1));
                vertices.put(uv.get(index * 2)).put(uv.get(index * 2 + 1));
                vertices.put(c & 1).put(c >> 1);
            }
            vertices.flip();
            bgfx.submit(vertices, shader, BGFX_STATE_WRITE_RGB | BGFX_STATE_WRITE_A | BGFX_STATE_PT_TRISTRIP | BGFX_STATE_BLEND_FUNC_SEPARATE(BGFX_STATE_BLEND_SRC_ALPHA, BGFX_STATE_BLEND_INV_SRC_ALPHA, BGFX_STATE_BLEND_ONE, BGFX_STATE_BLEND_INV_SRC_ALPHA));
        } finally {
            bgfxState.restore();
        }
    }

    /**
     * Uploads one borrowed light into a preselected shader slot.
     *
     * @param index validated slot between zero and four
     * @param light borrowed light; disabled lights upload zero intensity
     */
    private void setLight(int index, PointLight2D light) {
        shader.setUniform4f(LIGHT_NAMES[index], light.x, light.y, light.elevation, light.radius);
        shader.setUniform1f(ENERGY_NAMES[index], light.enabled ? light.intensity : 0);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        shader.dispose();
        bgfx.close();
    }
}
