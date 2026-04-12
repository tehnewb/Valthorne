package valthorne.graphics.lighting;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

public final class LightMesh {

    private final int vaoId;
    private final int vboId;
    private final FloatBuffer buffer;
    private int vertexCount;

    public LightMesh(int maxVertices) {
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

    public void setCircle(float centerX, float centerY, float radius, int rays, float r, float g, float b, float a) {
        buffer.clear();

        float step = (float) (Math.PI * 2.0 / rays);

        putVertex(centerX, centerY, 0f, 0f, r, g, b, a);

        for (int i = 0; i <= rays; i++) {
            float angle = i * step;
            float ex = centerX + (float) Math.cos(angle) * radius;
            float ey = centerY + (float) Math.sin(angle) * radius;
            float lx = radius == 0f ? 0f : (ex - centerX) / radius;
            float ly = radius == 0f ? 0f : (ey - centerY) / radius;
            putVertex(ex, ey, lx, ly, r, g, b, a);
        }

        upload();
    }

    public void setFan(float centerX, float centerY, float radius, float[] endX, float[] endY, float r, float g, float b, float a) {
        buffer.clear();

        int count = Math.min(endX.length, endY.length);
        if (count < 2) {
            vertexCount = 0;
            return;
        }

        float invRadius = radius == 0f ? 0f : 1f / radius;

        putVertex(centerX, centerY, 0f, 0f, r, g, b, a);

        for (int i = 0; i <= count; i++) {
            int idx = i == count ? 0 : i;

            float ex = endX[idx];
            float ey = endY[idx];
            float lx = (ex - centerX) * invRadius;
            float ly = (ey - centerY) * invRadius;

            putVertex(ex, ey, lx, ly, r, g, b, a);
        }

        upload();
    }

    private void upload() {
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
        glDrawArrays(GL_TRIANGLE_FAN, 0, vertexCount);
        glBindVertexArray(0);
    }

    public void dispose() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}