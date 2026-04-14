package valthorne.graphics.lighting;

import valthorne.graphics.Color;

public final class PointLight extends Light {

    public PointLight(RayHandler rayHandler, int rays, Color color, float distance, float x, float y) {
        super(rayHandler, rays, color, distance, x, y);
    }

    @Override
    public void update() {
        if (!active || !dirty) {
            return;
        }
        rebuild(false);
    }

    @Override
    protected void computeRayEnd(int index, float[] output) {
        float angle = (float) (index * (Math.PI * 2.0) / rays);
        output[0] = x + (float) Math.cos(angle) * distance;
        output[1] = y + (float) Math.sin(angle) * distance;
    }
}
