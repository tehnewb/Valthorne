package valthorne.graphics.lighting2d;

import org.lwjgl.BufferUtils;
import valthorne.Window;
import valthorne.graphics.render.RenderStateSnapshot3D;
import valthorne.graphics.shader.Shader;
import valthorne.graphics.shader.ShaderSources;
import valthorne.graphics.texture.TextureBatch;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Finite alpha-textured ground shadows for a dominant elevated light.
 * Call after ground drawing and before upright actors, within the lighting scene capture.
 * This is an artistic ground-overlay pass, not per-light visibility or 3D self-shadowing.
 * Multiple lights need a separate receiver/visibility pipeline; do not stack one overlay
 * per light. TextureBatch translation/custom clipping is not applied: pass world sprites
 * and the active Window projection, with normal framebuffer viewport/scissor settings.
 * Resources and draw calls belong to the creating GL thread.
 */
public final class GroundShadowRenderer2D implements AutoCloseable {
    private final Shader shader;
    private final int vao, vbo;
    private final FloatBuffer vertices = BufferUtils.createFloatBuffer(20);
    private boolean closed;
    private int drawCalls;
    private final PointLight2D blended = new PointLight2D();

    public GroundShadowRenderer2D() {
        try (var state = new RenderStateSnapshot3D()) {
            shader = new Shader(ShaderSources.load("lighting2d/ground-shadow.vert"), ShaderSources.load("lighting2d/ground-shadow.frag"));
            vao = glGenVertexArrays();
            vbo = glGenBuffers();
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, 80, GL_STREAM_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 20, 0);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 20, 12);
        }
    }

    public int getDrawCalls() {
        return drawCalls;
    }

    public void draw(TextureBatch sceneBatch, PointLight2D light, List<SpriteGroundShadow2D> casters) {
        draw(sceneBatch, light, casters, null);
    }

    /**
     * Artistic single-shadow approximation: nearby fill lights steer a weighted virtual source.
     * This is not independent per-light visibility; avoids stacked black shadows.
     */
    public void draw(TextureBatch sceneBatch, PointLight2D light, List<SpriteGroundShadow2D> casters, PointLight2D[] fills) {
        if (closed) throw new IllegalStateException("Ground shadow renderer is closed");
        drawCalls = 0;
        if (!light.enabled || !light.shadows || light.elevation <= 0 || light.intensity <= 0 || light.r + light.g + light.b <= 0)
            return;
        sceneBatch.flush();
        try (var state = new RenderStateSnapshot3D()) {
            glDisable(GL_DEPTH_TEST);
            glDepthMask(false);
            glDisable(GL_CULL_FACE);
            glEnable(GL_BLEND);
            glBlendEquation(GL_FUNC_ADD);
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
            glActiveTexture(GL_TEXTURE2);
            glBindSampler(2, 0);
            shader.bind();
            shader.setUniform1i("u_texture", 2);
            shader.setUniform1i("u_volume", fills == null ? 0 : 1);
            shader.setUniformMatrix4("u_projection", Window.getProjectionMatrix());
            shader.setUniform1f("u_softness", Math.min(2, light.sourceRadius * .15f));
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            for (int n = 0; n < casters.size(); n++) {
                SpriteGroundShadow2D caster = casters.get(n);
                if (!caster.enabled || caster.height <= 0 || caster.opacity <= 0 || caster.sprite.getColor().a() <= 0)
                    continue;
                var quad = caster.sprite.getVertexBuffer();
                var uv = caster.sprite.getUVBuffer();
                float dx = quad.get(2) - quad.get(0), dy = quad.get(3) - quad.get(1);
                float vx = quad.get(6) - quad.get(0), vy = quad.get(7) - quad.get(1);
                float baseX = quad.get(0) + caster.anchorU * dx + caster.anchorV * vx;
                float baseY = quad.get(1) + caster.anchorU * dy + caster.anchorV * vy;
                PointLight2D source = light;
                if (fills != null) {
                    float sum = weight(light, baseX, baseY), sx = light.x * sum, sy = light.y * sum, sz = light.elevation * sum;
                    for (PointLight2D fill : fills) {
                        float weight = weight(fill, baseX, baseY);
                        sum += weight;
                        sx += fill.x * weight;
                        sy += fill.y * weight;
                        sz += fill.elevation * weight;
                    }
                    if (sum > 0) {
                        blended.setPosition(sx / sum, sy / sum).setElevation(Math.max(80, sz / sum));
                        source = blended;
                    }
                }
                float distance = (float) Math.hypot(baseX - light.x, baseY - light.y);
                if (distance > light.radius + Math.abs(dx)) continue;
                distance = (float) Math.hypot(baseX - source.x, baseY - source.y);
                float height = SpriteGroundShadow2D.boundedHeight(caster.height, source.elevation, distance + Math.abs(dx) + Math.abs(vx), caster.maxLength);
                vertices.clear();
                for (int corner = 0; corner < 4; corner++) {
                    float u = (corner & 1), v = (corner & 2) == 0 ? caster.anchorV : caster.topV;
                    float z = (corner & 2) == 0 ? 0 : height;
                    float worldX = quad.get(0) + u * dx + v * vx;
                    // The lower card edge rests on the ground; its upper edge is vertical.
                    float groundY = baseY + (u - caster.anchorU) * dy;
                    float denominator = source.elevation - z;
                    vertices.put(source.elevation * worldX - z * source.x).put(source.elevation * groundY - z * source.y).put(denominator);
                    vertices.put(uv.get(0) + u * (uv.get(4) - uv.get(0))).put(uv.get(1) + v * (uv.get(5) - uv.get(1)));
                }
                vertices.flip();
                glBufferSubData(GL_ARRAY_BUFFER, 0, vertices);
                glBindTexture(GL_TEXTURE_2D, caster.sprite.getTexture().getTextureID());
                float halfU = .5f / caster.sprite.getTexture().getWidth(), halfV = .5f / caster.sprite.getTexture().getHeight();
                shader.setUniform4f("u_uvBounds", Math.min(uv.get(0), uv.get(4)) + halfU, Math.min(uv.get(1), uv.get(5)) + halfV, Math.max(uv.get(0), uv.get(4)) - halfU, Math.max(uv.get(1), uv.get(5)) - halfV);
                shader.setUniform1f("u_opacity", caster.opacity * caster.sprite.getColor().a());
                shader.setUniform1i("u_contact", 0);
                glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
                drawCalls++;
                if (fills == null) continue;
                // Compact receiver-space contact shadow stays beneath the trunk/feet.
                float radius = Math.max(7, Math.min(24, caster.height * .14f));
                vertices.clear();
                for (int c = 0; c < 4; c++) {
                    float u = c & 1, v = (c >> 1);
                    vertices.put(baseX + (u * 2 - 1) * radius).put(baseY + (v * 2 - 1) * radius * .4f).put(1).put(u).put(v);
                }
                vertices.flip();
                glBufferSubData(GL_ARRAY_BUFFER, 0, vertices);
                shader.setUniform1i("u_contact", 1);
                glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
                drawCalls++;
            }
        }
    }

    private static float weight(PointLight2D light, float x, float y) {
        if (!light.enabled || light.radius <= 0) return 0;
        float dx = x - light.x, dy = y - light.y;
        float attenuation = Math.max(0, 1 - (float) Math.sqrt(dx * dx + dy * dy + light.elevation * light.elevation) / light.radius);
        return attenuation * attenuation * light.intensity * (light.r * .2126f + light.g * .7152f + light.b * .0722f);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        shader.dispose();
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
