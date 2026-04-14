package valthorne.graphics.lighting;

import static org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN;


public final class LightMesh extends DynamicMesh2D {

    public LightMesh(int maxVertices) {
        super(maxVertices, GL_TRIANGLE_FAN);
    }

    public void setFan(float centerX, float centerY, float radius, float[] endX, float[] endY, float r, float g, float b, float a) {
        beginWrite();

        int count = Math.min(endX.length, endY.length);
        if (count < 2) {
            clearVertices();
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

        finishWrite();
    }
}
