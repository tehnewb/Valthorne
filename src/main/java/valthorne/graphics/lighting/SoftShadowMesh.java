package valthorne.graphics.lighting;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
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

public final class SoftShadowMesh {

    private static final float HIT_EPSILON = 0.999f;

    private final int vaoId;
    private final int vboId;
    private final FloatBuffer buffer;
    private int vertexCount;

    public SoftShadowMesh(int maxVertices) {
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
        buffer = BufferUtils.createFloatBuffer(maxVertices * 8);

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

    public void setTriangles(
            float centerX, float centerY,
            float radius, float softnessLength,
            float[] endX, float[] endY, float[] fractions,
            float r, float g, float b, float a
    ) {
        buffer.clear();

        float invRadius = radius == 0f ? 0f : 1f / radius;
        int count = endX.length;

        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;

            boolean hitA = fractions[i] < HIT_EPSILON;
            boolean hitB = fractions[next] < HIT_EPSILON;

            if (!hitA && !hitB) continue;

            float dx1 = endX[i] - centerX;
            float dy1 = endY[i] - centerY;
            float len1 = (float) Math.sqrt(dx1 * dx1 + dy1 * dy1);
            if (len1 == 0f) continue;

            float dx2 = endX[next] - centerX;
            float dy2 = endY[next] - centerY;
            float len2 = (float) Math.sqrt(dx2 * dx2 + dy2 * dy2);
            if (len2 == 0f) continue;

            float dirX1 = dx1 / len1;
            float dirY1 = dy1 / len1;

            float dirX2 = dx2 / len2;
            float dirY2 = dy2 / len2;

            float outerX1 = endX[i] + dirX1 * softnessLength;
            float outerY1 = endY[i] + dirY1 * softnessLength;

            float outerX2 = endX[next] + dirX2 * softnessLength;
            float outerY2 = endY[next] + dirY2 * softnessLength;

            float innerLocalX1 = dx1 * invRadius;
            float innerLocalY1 = dy1 * invRadius;

            float innerLocalX2 = dx2 * invRadius;
            float innerLocalY2 = dy2 * invRadius;

            float outerLocalX1 = (outerX1 - centerX) * invRadius;
            float outerLocalY1 = (outerY1 - centerY) * invRadius;

            float outerLocalX2 = (outerX2 - centerX) * invRadius;
            float outerLocalY2 = (outerY2 - centerY) * invRadius;

            float alphaA = hitA ? a : 0f;
            float alphaB = hitB ? a : 0f;

            // TRI 1
            putVertex(endX[i], endY[i], innerLocalX1, innerLocalY1, r, g, b, alphaA);
            putVertex(endX[next], endY[next], innerLocalX2, innerLocalY2, r, g, b, alphaB);
            putVertex(outerX1, outerY1, outerLocalX1, outerLocalY1, r, g, b, 0f);

            // TRI 2
            putVertex(outerX1, outerY1, outerLocalX1, outerLocalY1, r, g, b, 0f);
            putVertex(endX[next], endY[next], innerLocalX2, innerLocalY2, r, g, b, alphaB);
            putVertex(outerX2, outerY2, outerLocalX2, outerLocalY2, r, g, b, 0f);
        }

        buffer.flip();
        vertexCount = buffer.remaining() / 8;

        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void putVertex(float x, float y, float localX, float localY, float r, float g, float b, float a) {
        buffer.put(x);
        buffer.put(y);
        buffer.put(localX);
        buffer.put(localY);
        buffer.put(r);
        buffer.put(g);
        buffer.put(b);
        buffer.put(a);
    }

    public void render() {
        if (vertexCount == 0) return;
        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    public void dispose() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}