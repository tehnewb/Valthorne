package valthorne.graphics.lighting;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;

public final class SoftShadowMesh extends DynamicMesh2D {

    private static final float HIT_EPSILON = 0.999f;

    public SoftShadowMesh(int maxVertices) {
        super(maxVertices, GL_TRIANGLES);
    }

    public void setTriangles(float centerX, float centerY, float radius, float softnessLength, float[] endX, float[] endY, float[] fractions, float r, float g, float b, float a) {
        beginWrite();

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

        finishWrite();
    }
}
