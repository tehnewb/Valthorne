package valthorne.graphics.lighting;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

/**
 * Owns fixed-capacity CPU float storage, a vertex buffer, and a vertex array for
 * two-dimensional light geometry. Each vertex contains position XY, local XY, and RGBA
 * at attribute locations zero, one, and two. Subclasses write complete vertices and
 * upload them before rendering; storage does not grow automatically.
 *
 * <p>Creation, upload, drawing, and disposal require the owning graphics context.
 * Buffer and vertex-array helpers unbind to zero rather than restoring prior bindings.
 * Dispose once after use; the stored native names are not cleared for repeated calls.</p>
 *
 * @author Albert Beaupre
 */
abstract class DynamicMesh2D {

    protected final FloatBuffer buffer; // Fixed-capacity interleaved CPU vertex storage.
    private final int vaoId; // Owned vertex-array name.
    private final int vboId; // Owned dynamic vertex-buffer name.
    private final int drawMode; // Primitive mode used by render.
    private int vertexCount; // Uploaded complete vertex count.

    /**
     * Allocates fixed storage for eight floats per vertex and configures the vertex layout.
     * The caller supplies a valid positive capacity and OpenGL primitive mode.
     *
     * @param maxVertices maximum writable vertex count
     * @param drawMode OpenGL draw primitive constant
     */
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

    /**
     * Resets CPU buffer position and limit for overwriting. Existing GPU data and draw
     * count remain unchanged until finishWrite or clearVertices.
     */
    protected final void beginWrite() {
        buffer.clear();
    }

    /**
     * Appends one interleaved vertex to CPU storage without bounds growth or uploading.
     *
     * @param x position X
     * @param y position Y
     * @param localX local shading coordinate X
     * @param localY local shading coordinate Y
     * @param r red component
     * @param g green component
     * @param b blue component
     * @param a alpha component
     * @throws java.nio.BufferOverflowException if fixed storage is exhausted
     */
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

    /**
     * Flips CPU storage, derives the complete eight-float vertex count, and uploads the
     * written range from offset zero. Unbinds the array-buffer target afterward.
     */
    protected final void finishWrite() {
        buffer.flip();
        vertexCount = buffer.remaining() / 8;

        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Sets the draw count to zero without clearing CPU or GPU storage.
     */
    protected final void clearVertices() {
        vertexCount = 0;
    }

    /**
     * Draws the uploaded vertex count with the configured primitive mode. A zero count
     * does nothing. The caller supplies shader and other draw state; this method binds
     * its VAO and resets that binding to zero afterward.
     */
    public final void render() {
        if (vertexCount == 0) {
            return;
        }

        glBindVertexArray(vaoId);
        glDrawArrays(drawMode, 0, vertexCount);
        glBindVertexArray(0);
    }

    /**
     * Deletes the owned vertex buffer and vertex array. Does not clear stored names or
     * track disposal, so the owner must call it once and avoid subsequent use.
     */
    public void dispose() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
