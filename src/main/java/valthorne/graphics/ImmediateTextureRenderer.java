package valthorne.graphics;

import org.lwjgl.BufferUtils;
import valthorne.Window;
import valthorne.graphics.shader.TexturedQuadShader;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Shared immediate quad renderer used by standalone textured draw helpers.
 *
 * <p>The renderer accepts quad-ordered position and UV buffers, expands them to triangles,
 * uploads them to a small dynamic VBO, and renders through either the currently bound
 * textured-quad shader or an internal fallback shader.</p>
 *
 * @author Albert Beaupre
 * @since August 15th, 2026
 */
public final class ImmediateTextureRenderer {

    /**
     * Interleaved width: XY position, UV coordinates, and RGBA tint.
     */
    private static final int FLOATS_PER_VERTEX = 8;
    /**
     * Six triangle vertices emitted for each four-corner quad.
     */
    private static final int VERTICES_PER_QUAD = 6;
    /**
     * Total staged float count per expanded quad.
     */
    private static final int FLOATS_PER_QUAD = FLOATS_PER_VERTEX * VERTICES_PER_QUAD;
    /**
     * Byte width of an interleaved floating-point component.
     */
    private static final int BYTES_PER_FLOAT = 4;

    /**
     * Shared owned vertex-array name, zero before initialization or after disposal.
     */
    private static int vao;
    /**
     * Shared owned streaming vertex-buffer name.
     */
    private static int vbo;
    /**
     * Current staging and GPU capacity in quads.
     */
    private static int quadCapacity;
    /**
     * Shared direct CPU triangle staging storage.
     */
    private static FloatBuffer vertexBuffer;
    /**
     * Owned fallback shader used when no program is bound.
     */
    private static TexturedQuadShader defaultShader;

    /**
     * Prevents construction of this shared context-bound quad renderer.
     */
    private ImmediateTextureRenderer() {
    }

    /**
     * Deletes the shared fallback program, vertex array, and buffer, then clears
     * CPU staging state. Repeated successful disposal is harmless. The next draw
     * lazily allocates a new set; call with the owning GL context current.
     */
    public static void dispose() {
        if (defaultShader != null) {
            defaultShader.dispose();
            defaultShader = null;
        }
        if (vao != 0) {
            glDeleteVertexArrays(vao);
            vao = 0;
        }
        if (vbo != 0) {
            glDeleteBuffers(vbo);
            vbo = 0;
        }
        quadCapacity = 0;
        vertexBuffer = null;
    }

    /**
     * Expands each four-corner quad to triangles (0,1,2) and (2,3,0), uploads copied
     * positions/UVs and a uniform vertex tint, then draws with the current program
     * or a fallback textured-quad shader when no program is bound.
     * <p>
     * Buffers are read by absolute indices starting at zero; their current positions
     * are ignored and preserved. Each must contain at least eight floats per quad.
     * The method sets standard sampler/projection uniforms when present, binds the
     * texture on unit zero, and leaves array bindings at zero. It does not generally
     * restore GL state or configure blending/depth policy.
     * </p>
     *
     * @param textureID borrowed 2D texture name
     * @param positions four XY corners per quad, starting at index zero
     * @param uvs       corresponding UV pairs
     * @param quadCount number of quads; nonpositive values return immediately
     * @param color     tint copied to each vertex, or null for white
     * @throws NullPointerException      if a required buffer is null for positive quadCount
     * @throws IndexOutOfBoundsException if an input buffer has insufficient readable elements
     */
    public static void drawQuads(int textureID, FloatBuffer positions, FloatBuffer uvs, int quadCount, Color color) {
        if (quadCount <= 0) return;
        if (positions == null) throw new NullPointerException("positions");
        if (uvs == null) throw new NullPointerException("uvs");

        ensureInitialized();
        ensureCapacity(quadCount);

        int activeProgram = glGetInteger(GL_CURRENT_PROGRAM);
        boolean boundFallbackShader = false;

        if (activeProgram == 0) {
            defaultShader.bind();
            activeProgram = defaultShader.getProgramID();
            boundFallbackShader = true;
        }

        applyStandardUniforms(activeProgram);
        uploadVertices(positions, uvs, quadCount, color);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureID);

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBuffer);
        glDrawArrays(GL_TRIANGLES, 0, quadCount * VERTICES_PER_QUAD);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        if (boundFallbackShader) {
            defaultShader.unbind();
        }
    }

    /**
     * Lazily creates the shared fallback shader, one-quad CPU/GPU capacity, and
     * position/UV/color attributes. Uses the current context; shared static storage
     * is not suitable for unsynchronized or unrelated-context access.
     */
    private static void ensureInitialized() {
        if (vao != 0) return;

        defaultShader = new TexturedQuadShader();
        quadCapacity = 1;
        vertexBuffer = BufferUtils.createFloatBuffer(FLOATS_PER_QUAD);

        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) FLOATS_PER_QUAD * BYTES_PER_FLOAT, GL_STREAM_DRAW);

        int stride = FLOATS_PER_VERTEX * BYTES_PER_FLOAT;

        glEnableVertexAttribArray(TexturedQuadShader.ATTR_POSITION);
        glVertexAttribPointer(TexturedQuadShader.ATTR_POSITION, 2, GL_FLOAT, false, stride, 0L);

        glEnableVertexAttribArray(TexturedQuadShader.ATTR_UV);
        glVertexAttribPointer(TexturedQuadShader.ATTR_UV, 2, GL_FLOAT, false, stride, 2L * BYTES_PER_FLOAT);

        glEnableVertexAttribArray(TexturedQuadShader.ATTR_COLOR);
        glVertexAttribPointer(TexturedQuadShader.ATTR_COLOR, 4, GL_FLOAT, false, stride, 4L * BYTES_PER_FLOAT);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    /**
     * Grows shared CPU and GPU storage to exactly the requested quad capacity when
     * the current allocation is too small. Existing staging data is discarded.
     *
     * @param quadCount required quad capacity
     */
    private static void ensureCapacity(int quadCount) {
        if (quadCount <= quadCapacity) return;

        quadCapacity = quadCount;
        vertexBuffer = BufferUtils.createFloatBuffer(quadCapacity * FLOATS_PER_QUAD);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) quadCapacity * FLOATS_PER_QUAD * BYTES_PER_FLOAT, GL_STREAM_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Sets the active program's texture sampler to unit zero and projection to
     * Window's current matrix, skipping uniform names absent from the program.
     *
     * @param programID currently bound program name
     */
    private static void applyStandardUniforms(int programID) {
        int textureLocation = glGetUniformLocation(programID, TexturedQuadShader.UNIFORM_TEXTURE);
        if (textureLocation != -1) {
            glUniform1i(textureLocation, 0);
        }

        int projectionLocation = glGetUniformLocation(programID, TexturedQuadShader.UNIFORM_MVP);
        if (projectionLocation != -1) {
            glUniformMatrix4fv(projectionLocation, false, Window.getProjectionMatrix());
        }
    }

    /**
     * Builds interleaved triangle vertices from absolute input-buffer coordinates,
     * using white for a null tint, and flips shared staging storage for upload.
     *
     * @param positions four XY corners per quad
     * @param uvs       matching UV pairs
     * @param quadCount number of quads
     * @param color     common tint, or null
     */
    private static void uploadVertices(FloatBuffer positions, FloatBuffer uvs, int quadCount, Color color) {
        vertexBuffer.clear();

        float r = color != null ? color.r() : 1f;
        float g = color != null ? color.g() : 1f;
        float b = color != null ? color.b() : 1f;
        float a = color != null ? color.a() : 1f;

        for (int i = 0; i < quadCount; i++) {
            int base = i * 8;
            putIndexedVertex(positions, uvs, base, 0, r, g, b, a);
            putIndexedVertex(positions, uvs, base, 1, r, g, b, a);
            putIndexedVertex(positions, uvs, base, 2, r, g, b, a);
            putIndexedVertex(positions, uvs, base, 2, r, g, b, a);
            putIndexedVertex(positions, uvs, base, 3, r, g, b, a);
            putIndexedVertex(positions, uvs, base, 0, r, g, b, a);
        }

        vertexBuffer.flip();
    }

    /**
     * Copies one selected corner's position/UV and supplied RGBA into shared staging.
     * Input buffer positions are not changed.
     *
     * @param positions   absolute-index position source
     * @param uvs         absolute-index UV source
     * @param quadBase    float offset of the quad
     * @param vertexIndex corner index from zero through three
     * @param r           red tint
     * @param g           green tint
     * @param b           blue tint
     * @param a           alpha tint
     */
    private static void putIndexedVertex(FloatBuffer positions, FloatBuffer uvs, int quadBase, int vertexIndex, float r, float g, float b, float a) {
        int index = quadBase + vertexIndex * 2;
        vertexBuffer.put(positions.get(index));
        vertexBuffer.put(positions.get(index + 1));
        vertexBuffer.put(uvs.get(index));
        vertexBuffer.put(uvs.get(index + 1));
        vertexBuffer.put(r).put(g).put(b).put(a);
    }
}
