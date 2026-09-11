package valthorne.graphics.lighting;

import static org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN;


/**
 * Uploads a closed triangle fan for a two-dimensional light footprint. The first
 * vertex is the light center, followed by ordered ray endpoints and a repeated first
 * endpoint. Local shading coordinates are endpoint offsets divided by radius.
 * The mesh owns GPU resources through its base class and requires a graphics context.
 *
 * @author Albert Beaupre
 */
public final class LightMesh extends DynamicMesh2D {

    /**
     * Allocates a triangle-fan mesh with fixed vertex capacity. A fan with N endpoints
     * needs N+2 vertices, including its center and closing endpoint.
     *
     * @param maxVertices capacity in vertices
     */
    public LightMesh(int maxVertices) {
        super(maxVertices, GL_TRIANGLE_FAN);
    }

    /**
     * Overwrites and uploads a closed fan using the shorter endpoint-array length.
     * Fewer than two endpoints clear the draw count. Endpoint order is not sorted here;
     * the caller supplies perimeter order. Zero radius produces zero local coordinates.
     *
     * @param centerX world-space center X
     * @param centerY world-space center Y
     * @param radius divisor for local radial coordinates
     * @param endX ordered endpoint X coordinates
     * @param endY ordered endpoint Y coordinates
     * @param r red light component
     * @param g green light component
     * @param b blue light component
     * @param a alpha light component
     * @throws NullPointerException if either endpoint array is null
     * @throws java.nio.BufferOverflowException if fan exceeds fixed capacity
     */
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
