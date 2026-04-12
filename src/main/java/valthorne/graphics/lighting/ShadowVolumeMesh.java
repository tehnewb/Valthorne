package valthorne.graphics.lighting;

import org.lwjgl.BufferUtils;
import valthorne.math.Vector2f;
import valthorne.math.geometry.Area;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public final class ShadowVolumeMesh {

    private final int vaoId;
    private final int vboId;
    private final FloatBuffer buffer;
    private int vertexCount;

    public ShadowVolumeMesh(int maxVertices) {
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

    public void build(float lightX, float lightY, float length, List<Area> occluders) {
        buffer.clear();

        for (Area area : occluders) {
            Vector2f[] pts = area.points();
            if (pts == null || pts.length < 2) continue;

            for (int i = 0; i < pts.length; i++) {
                Vector2f a = pts[i];
                Vector2f b = pts[(i + 1) % pts.length];

                float ax = a.getX();
                float ay = a.getY();
                float bx = b.getX();
                float by = b.getY();

                float edgeX = bx - ax;
                float edgeY = by - ay;

                float normalX = -edgeY;
                float normalY = edgeX;

                float toLightX = lightX - ax;
                float toLightY = lightY - ay;

                if (normalX * toLightX + normalY * toLightY > 0f) continue;

                float dirAX = ax - lightX;
                float dirAY = ay - lightY;
                float lenA = (float) Math.sqrt(dirAX * dirAX + dirAY * dirAY);
                if (lenA == 0f) continue;
                dirAX /= lenA;
                dirAY /= lenA;

                float dirBX = bx - lightX;
                float dirBY = by - lightY;
                float lenB = (float) Math.sqrt(dirBX * dirBX + dirBY * dirBY);
                if (lenB == 0f) continue;
                dirBX /= lenB;
                dirBY /= lenB;

                float exA = ax + dirAX * length;
                float eyA = ay + dirAY * length;

                float exB = bx + dirBX * length;
                float eyB = by + dirBY * length;

                put(ax, ay, 1f);
                put(bx, by, 1f);
                put(exA, eyA, 1f);

                put(exA, eyA, 1f);
                put(bx, by, 1f);
                put(exB, eyB, 1f);
            }
        }

        buffer.flip();
        vertexCount = buffer.remaining() / 8;

        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void put(float x, float y, float a) {
        buffer.put(x);
        buffer.put(y);
        buffer.put(0f);
        buffer.put(0f);
        buffer.put(0f);
        buffer.put(0f);
        buffer.put(0f);
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