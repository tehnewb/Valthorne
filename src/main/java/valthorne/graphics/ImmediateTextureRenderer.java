package valthorne.graphics;

import org.lwjgl.BufferUtils;
import valthorne.Window;
import valthorne.graphics.shader.TexturedQuadShader;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STREAM_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

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

    private static final int FLOATS_PER_VERTEX = 8;
    private static final int VERTICES_PER_QUAD = 6;
    private static final int FLOATS_PER_QUAD = FLOATS_PER_VERTEX * VERTICES_PER_QUAD;
    private static final int BYTES_PER_FLOAT = 4;

    private static int vao;
    private static int vbo;
    private static int quadCapacity;
    private static FloatBuffer vertexBuffer;
    private static TexturedQuadShader defaultShader;

    private ImmediateTextureRenderer() {
    }

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

    private static void ensureCapacity(int quadCount) {
        if (quadCount <= quadCapacity) return;

        quadCapacity = quadCount;
        vertexBuffer = BufferUtils.createFloatBuffer(quadCapacity * FLOATS_PER_QUAD);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) quadCapacity * FLOATS_PER_QUAD * BYTES_PER_FLOAT, GL_STREAM_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

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

    private static void putIndexedVertex(FloatBuffer positions, FloatBuffer uvs, int quadBase, int vertexIndex, float r, float g, float b, float a) {
        int index = quadBase + vertexIndex * 2;
        vertexBuffer.put(positions.get(index));
        vertexBuffer.put(positions.get(index + 1));
        vertexBuffer.put(uvs.get(index));
        vertexBuffer.put(uvs.get(index + 1));
        vertexBuffer.put(r).put(g).put(b).put(a);
    }
}
