package valthorne.graphics.lighting2d;

import valthorne.Window;
import valthorne.graphics.OpenGLStateSnapshot;
import valthorne.graphics.shader.Shader;
import valthorne.io.file.ValthorneFiles;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureBatch;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * Finite alpha-textured ground shadows for a dominant elevated light.
 * Call after ground drawing and before upright actors, within the lighting scene capture.
 * This is an artistic ground-overlay pass, not per-light visibility or 3D self-shadowing.
 * Multiple lights need a separate receiver/visibility pipeline; do not stack one overlay
 * per light. TextureBatch translation/custom clipping is not applied: pass world sprites
 * and the active Window projection, with normal framebuffer viewport/scissor settings.
 * Resources and draw calls belong to the creating GL thread.
 *
 * <p>Consecutive cards sharing a texture are submitted as ordered instances,
 * including interleaved contact shadows. A texture change or 1024 queued quads
 * flushes the batch without sorting painter order. The five-tap alpha filter,
 * projection and blend equations are unchanged. Streaming storage is allocated
 * once (104 KiB CPU staging, at most 104 KiB per GPU upload); queueing a card
 * allocates no Java objects. The existing pass-state snapshot is taken once per
 * draw invocation, not once per caster.</p>
 */
public final class GroundShadowRenderer2D implements AutoCloseable {
    /**
     * Maximum projected/contact quads submitted in one instanced draw.
     */
    private static final int BATCH_CAPACITY = 1024;
    /**
     * Four interleaved homogeneous XYZ/UV vertices, bounds, opacity and contact flag.
     */
    private static final int INSTANCE_FLOATS = 26;
    /**
     * Byte stride shared by the instance attributes and streaming buffer.
     */
    private static final int INSTANCE_BYTES = INSTANCE_FLOATS * Float.BYTES;
    private final Shader shader; // Owned shader for this rendering pass.
    private final int vao; // Owned vertex array describing pass geometry.
    private final int vbo; // Owned streaming vertex buffer.
    private final FloatBuffer vertices; // Owned packed instance storage, released explicitly on close.
    private boolean closed; // Whether the owned GL resources were released.
    private int drawCalls; // Draw count in the latest shadow pass.
    private int shadowCount; // Projected/contact quads rendered in the latest pass.
    private int instanceCount; // Complete quads currently queued in the streaming buffer.
    private int texture; // Borrowed texture used by the pending ordered batch.
    private final PointLight2D blended = new PointLight2D(); // Reusable virtual source blended from nearby lights.

    /**
     * Creates the shader and streaming quad resources on the current GL context,
     * restoring the caller's selected bindings afterward. Close on the same
     * graphics thread after the final pass.
     */
    public GroundShadowRenderer2D() {
        OpenGLStateSnapshot state = new OpenGLStateSnapshot();
        Shader createdShader = null;
        int createdVao = 0;
        int createdVbo = 0;
        FloatBuffer createdVertices = null;
        try {
            createdShader = new Shader(ValthorneFiles.readString("valthorne/shaders/lighting2d/ground-shadow.vert"), ValthorneFiles.readString("valthorne/shaders/lighting2d/ground-shadow.frag"));
            createdVao = glGenVertexArrays();
            createdVbo = glGenBuffers();
            createdVertices = memAllocFloat(BATCH_CAPACITY * INSTANCE_FLOATS);
            glBindVertexArray(createdVao);
            glBindBuffer(GL_ARRAY_BUFFER, createdVbo);
            glBufferData(GL_ARRAY_BUFFER, (long) BATCH_CAPACITY * INSTANCE_BYTES, GL_STREAM_DRAW);
            for (int attribute = 0; attribute < 10; attribute++) {
                int components = attribute < 4 ? 3 : attribute < 8 ? 2 : attribute == 8 ? 4 : 2;
                int offset = attribute < 4 ? attribute * 5 : attribute < 8 ? (attribute - 4) * 5 + 3 : attribute == 8 ? 20 : 24;
                glEnableVertexAttribArray(attribute);
                glVertexAttribPointer(attribute, components, GL_FLOAT, false, INSTANCE_BYTES, (long) offset * Float.BYTES);
                glVertexAttribDivisor(attribute, 1);
            }
            shader = createdShader;
            vao = createdVao;
            vbo = createdVbo;
            vertices = createdVertices;
        } catch (RuntimeException | Error failure) {
            if (createdVertices != null) memFree(createdVertices);
            if (createdVbo != 0) glDeleteBuffers(createdVbo);
            if (createdVao != 0) glDeleteVertexArrays(createdVao);
            if (createdShader != null) createdShader.dispose();
            throw failure;
        } finally {
            state.restore();
        }
    }

    /**
     * Returns the draw count from the latest shadow pass.
     *
     * @return actual instanced batch submissions, not individual shadow quads
     */
    public int getDrawCalls() {
        return drawCalls;
    }

    /**
     * Returns the number of projected and contact quads submitted by the latest pass.
     * A single batch may contain many quads; culled/disabled cards are excluded.
     *
     * @return rendered shadow quad count
     */
    public int getShadowCount() {
        return shadowCount;
    }

    /**
     * Draws alpha-textured ground shadows from one dominant light.
     *
     * @param sceneBatch active scene batch, flushed before external drawing
     * @param light borrowed dominant elevated light
     * @param casters borrowed shadow cards in painter order
     */
    public void draw(TextureBatch sceneBatch, PointLight2D light, List<SpriteGroundShadow2D> casters) {
        draw(sceneBatch, light, casters, null);
    }

    /**
     * Artistic single-shadow approximation: nearby fill lights steer a weighted virtual source.
     * This is not independent per-light visibility; avoids stacked black shadows.
     *
     * @param sceneBatch active scene batch flushed before this external pass
     * @param light borrowed dominant elevated light
     * @param casters borrowed shadow cards in painter order
     * @param fills borrowed supplementary lights, or null for a single-source pass
     * @throws IllegalStateException if this renderer has been closed
     */
    public void draw(TextureBatch sceneBatch, PointLight2D light, List<SpriteGroundShadow2D> casters, PointLight2D[] fills) {
        if (closed) throw new IllegalStateException("Ground shadow renderer is closed");
        drawCalls = 0;
        shadowCount = 0;
        if (!light.enabled || !light.shadows || light.elevation <= 0 || light.intensity <= 0 || light.r + light.g + light.b <= 0)
            return;
        sceneBatch.flush();
        OpenGLStateSnapshot state = new OpenGLStateSnapshot();
        try {
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
                        blended.setPosition(sx / sum, sy / sum)
                                .setElevation(Math.max(80, sz / sum));
                        source = blended;
                    }
                }
                float distance = (float) Math.hypot(baseX - light.x, baseY - light.y);
                if (distance > light.radius + Math.abs(dx)) continue;
                distance = (float) Math.hypot(baseX - source.x, baseY - source.y);
                float height = SpriteGroundShadow2D.boundedHeight(caster.height, source.elevation, distance + Math.abs(dx) + Math.abs(vx), caster.maxLength);
                Texture casterImage = caster.sprite.getTexture();
                int casterTexture = casterImage.getTextureID();
                float halfU = .5f / casterImage.getWidth(), halfV = .5f / casterImage.getHeight();
                float minU = Math.min(uv.get(0), uv.get(4)) + halfU;
                float minV = Math.min(uv.get(1), uv.get(5)) + halfV;
                float maxU = Math.max(uv.get(0), uv.get(4)) - halfU;
                float maxV = Math.max(uv.get(1), uv.get(5)) - halfV;
                float opacity = caster.opacity * caster.sprite.getColor().a();
                beginQuad(casterTexture);
                for (int corner = 0; corner < 4; corner++) {
                    float u = (corner & 1), v = (corner & 2) == 0 ? caster.anchorV : caster.topV;
                    float z = (corner & 2) == 0 ? 0 : height;
                    float worldX = quad.get(0) + u * dx + v * vx;
                    // The lower card edge rests on the ground; its upper edge is vertical.
                    float groundY = baseY + (u - caster.anchorU) * dy;
                    float denominator = source.elevation - z;
                    vertices.put(source.elevation * worldX - z * source.x)
                            .put(source.elevation * groundY - z * source.y)
                            .put(denominator)
                            .put(uv.get(0) + u * (uv.get(4) - uv.get(0)))
                            .put(uv.get(1) + v * (uv.get(5) - uv.get(1)));
                }
                vertices.put(minU)
                        .put(minV)
                        .put(maxU)
                        .put(maxV)
                        .put(opacity)
                        .put(0);
                instanceCount++;
                shadowCount++;
                if (fills == null) continue;
                // Compact receiver-space contact shadow stays beneath the trunk/feet.
                float radius = Math.max(7, Math.min(24, caster.height * .14f));
                beginQuad(casterTexture);
                for (int c = 0; c < 4; c++) {
                    float u = c & 1, v = (c >> 1);
                    vertices.put(baseX + (u * 2 - 1) * radius)
                            .put(baseY + (v * 2 - 1) * radius * .4f)
                            .put(1)
                            .put(u)
                            .put(v);
                }
                vertices.put(minU)
                        .put(minV)
                        .put(maxU)
                        .put(maxV)
                        .put(opacity)
                        .put(1);
                instanceCount++;
                shadowCount++;
            }
            flush();
        } finally {
            instanceCount = 0;
            vertices.clear();
            state.restore();
        }
    }

    /**
     * Makes room for a complete instance while retaining input painter order.
     *
     * @param nextTexture borrowed texture for the next quad
     */
    private void beginQuad(int nextTexture) {
        if (instanceCount == BATCH_CAPACITY || instanceCount != 0 && texture != nextTexture) flush();
        texture = nextTexture;
    }

    /**
     * Uploads and draws one ordered texture run. Orphaning avoids overwriting
     * storage still consumed by an earlier submission; the native staging buffer
     * is retained and immediately reused after the upload returns. Only the used
     * prefix is uploaded, so short texture runs do not allocate full-size GPU batches.
     */
    private void flush() {
        if (instanceCount == 0) return;
        vertices.flip();
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STREAM_DRAW);
        glBindTexture(GL_TEXTURE_2D, texture);
        glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, instanceCount);
        drawCalls++;
        instanceCount = 0;
        vertices.clear();
    }

    /**
     * Estimates a light's luminance contribution at one ground coordinate.
     *
     * @param light borrowed candidate light
     * @param x receiver world X
     * @param y receiver world Y
     * @return nonnegative attenuation-weighted luminance
     */
    private static float weight(PointLight2D light, float x, float y) {
        /*
         * Disabled and degenerate lights contribute nothing; squared falloff
         * weights the virtual source without allocating intermediate vectors.
         */
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
        memFree(vertices);
    }
}
