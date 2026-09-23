package valthorne.graphics.lighting2d;

import org.lwjgl.BufferUtils;
import valthorne.Window;
import valthorne.graphics.render.RenderStateSnapshot3D;
import valthorne.graphics.shader.Shader;
import valthorne.graphics.shader.ShaderSources;
import valthorne.graphics.texture.TextureBatch;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Stylized billboard volume shading, without generated normal textures.
 * Uses authored foot/top anchors and a procedural rounded surface, not reconstructed geometry.
 * Call in painter order inside the scene capture. Borrows sprites; owns only GL resources.
 * Honors sprite transforms/UVs, but not TextureBatch-local clipping or translation.
 */
public final class SpriteVolumeRenderer2D implements AutoCloseable {
    private final Shader shader;
    private final int vao, vbo;
    private final FloatBuffer vertices = BufferUtils.createFloatBuffer(24);
    private boolean closed;
    private static final String[] LIGHT_NAMES = {"u_lights[0]", "u_lights[1]", "u_lights[2]", "u_lights[3]", "u_lights[4]"};
    private static final String[] ENERGY_NAMES = {"u_energy[0]", "u_energy[1]", "u_energy[2]", "u_energy[3]", "u_energy[4]"};

    public SpriteVolumeRenderer2D() {
        try (var state = new RenderStateSnapshot3D()) {
            shader = new Shader(ShaderSources.load("lighting2d/sprite-volume.vert"), ShaderSources.load("lighting2d/sprite-volume.frag"));
            vao = glGenVertexArrays();
            vbo = glGenBuffers();
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, 96, GL_STREAM_DRAW);
            for (int i = 0; i < 3; i++) {
                glEnableVertexAttribArray(i);
                glVertexAttribPointer(i, 2, GL_FLOAT, false, 24, i * 8L);
            }
        }
    }

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
        try (var state = new RenderStateSnapshot3D()) {
            glDisable(GL_DEPTH_TEST);
            glDepthMask(false);
            glDisable(GL_CULL_FACE);
            glEnable(GL_BLEND);
            glBlendEquation(GL_FUNC_ADD);
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            glActiveTexture(GL_TEXTURE2);
            glBindSampler(2, 0);
            glBindTexture(GL_TEXTURE_2D, sprite.getTexture().getTextureID());
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
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferSubData(GL_ARRAY_BUFFER, 0, vertices);
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        }
    }

    private void setLight(int index, PointLight2D light) {
        shader.setUniform4f(LIGHT_NAMES[index], light.x, light.y, light.elevation, light.radius);
        shader.setUniform1f(ENERGY_NAMES[index], light.enabled ? light.intensity : 0);
    }

    public void close() {
        if (closed) return;
        closed = true;
        shader.dispose();
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
