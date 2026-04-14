package valthorne.graphics.lighting;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

abstract class DynamicMesh2D {

    protected final FloatBuffer buffer;
    private final int vaoId;
    private final int vboId;
    private final int drawMode;
    private int vertexCount;

    protected DynamicMesh2D(int maxVertices, int drawMode) {
        this.drawMode = drawMode;
        this.vaoId = glGenVertexArrays();
        this.vboId = glGenBuffers();
        this.buffer = BufferUtils.createFloatBuffer(maxVertices * 8);

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, (long) maxVertices * 8L * Float.BYTES, GL_DYNAMIC_DRAW);

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 8 * Float.BYTES, 0L);

        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 8 * Float.BYTES, 2L * Float.BYTES);

        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, 8 * Float.BYTES, 4L * Float.BYTES);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    protected final void beginWrite() {
        buffer.clear();
    }

    protected final void putVertex(float x, float y, float localX, float localY, float r, float g, float b, float a) {
        buffer.put(x);
        buffer.put(y);
        buffer.put(localX);
        buffer.put(localY);
        buffer.put(r);
        buffer.put(g);
        buffer.put(b);
        buffer.put(a);
    }

    protected final void finishWrite() {
        buffer.flip();
        vertexCount = buffer.remaining() / 8;

        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    protected final void clearVertices() {
        vertexCount = 0;
    }

    public final void render() {
        if (vertexCount == 0) {
            return;
        }

        glBindVertexArray(vaoId);
        glDrawArrays(drawMode, 0, vertexCount);
        glBindVertexArray(0);
    }

    public void dispose() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
