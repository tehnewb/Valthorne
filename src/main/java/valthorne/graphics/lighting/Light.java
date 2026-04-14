package valthorne.graphics.lighting;

import valthorne.graphics.Color;

import java.util.Collections;
import java.util.List;

public abstract class Light {

    public static final int ALL_MASK_BITS = ~0;

    protected final RayHandler rayHandler;
    protected final Color color;
    protected int rays;
    protected float distance;
    protected float x;
    protected float y;
    protected boolean active = true;
    protected boolean xray;
    protected boolean soft = true;
    protected float softnessLength = 16f;
    protected float[] endX;
    protected float[] endY;
    protected float[] fractions;
    protected int categoryBits = ALL_MASK_BITS;
    protected int occlusionMaskBits = ALL_MASK_BITS;
    protected boolean dirty = true;
    private final float[] scratchRayEnd = new float[2];
    private final RayCastHit scratchHit = new RayCastHit();

    protected Light(RayHandler rayHandler, int rays, Color color, float distance, float x, float y) {
        if (rayHandler == null) throw new NullPointerException("rayHandler cannot be null");
        if (color == null) throw new NullPointerException("color cannot be null");
        if (rays < 3) throw new IllegalArgumentException("rays must be >= 3");

        this.rayHandler = rayHandler;
        this.rays = rays;
        this.color = color.copy();
        this.distance = distance;
        this.x = x;
        this.y = y;

        ensureRayCapacity(rays);
    }

    public abstract void update();

    protected abstract void computeRayEnd(int index, float[] output);

    protected void rebuild() {
        rebuild(true);
    }

    protected final void rebuild(boolean sortEndpoints) {
        for (int i = 0; i < rays; i++) {
            computeRayEnd(i, scratchRayEnd);

            float targetX = scratchRayEnd[0];
            float targetY = scratchRayEnd[1];
            applyRayResult(i, targetX, targetY);
        }

        if (sortEndpoints) {
            sortEndpointsByAngle();
        }
        dirty = false;
    }

    protected void sortEndpointsByAngle() {
        int count = endX.length;
        if (count < 2) return;

        float[] angles = new float[count];
        int[] indices = new int[count];

        for (int i = 0; i < count; i++) {
            angles[i] = (float) Math.atan2(endY[i] - y, endX[i] - x);
            indices[i] = i;
        }

        quickSort(indices, angles, 0, count - 1);

        float[] sortedEndX = new float[count];
        float[] sortedEndY = new float[count];
        float[] sortedFractions = new float[count];

        for (int i = 0; i < count; i++) {
            int index = indices[i];
            sortedEndX[i] = endX[index];
            sortedEndY[i] = endY[index];
            sortedFractions[i] = fractions[index];
        }

        System.arraycopy(sortedEndX, 0, endX, 0, count);
        System.arraycopy(sortedEndY, 0, endY, 0, count);
        System.arraycopy(sortedFractions, 0, fractions, 0, count);
    }

    private void quickSort(int[] indices, float[] angles, int low, int high) {
        while (low < high) {
            int pivotIndex = partition(indices, angles, low, high);

            if (pivotIndex - low < high - pivotIndex) {
                quickSort(indices, angles, low, pivotIndex - 1);
                low = pivotIndex + 1;
            } else {
                quickSort(indices, angles, pivotIndex + 1, high);
                high = pivotIndex - 1;
            }
        }
    }

    private int partition(int[] indices, float[] angles, int low, int high) {
        float pivot = angles[indices[high]];
        int i = low - 1;

        for (int j = low; j < high; j++) {
            if (angles[indices[j]] <= pivot) {
                i++;
                int temp = indices[i];
                indices[i] = indices[j];
                indices[j] = temp;
            }
        }

        int temp = indices[i + 1];
        indices[i + 1] = indices[high];
        indices[high] = temp;

        return i + 1;
    }

    protected final void applyRayResult(int index, float targetX, float targetY) {
        if (rayCast(targetX, targetY, scratchHit)) {
            endX[index] = scratchHit.getX();
            endY[index] = scratchHit.getY();
            fractions[index] = scratchHit.getFraction();
            return;
        }

        endX[index] = targetX;
        endY[index] = targetY;
        fractions[index] = 1f;
    }

    protected final boolean rayCast(float targetX, float targetY, RayCastHit outHit) {
        RayCastWorld world = rayHandler.getRayCastWorld();
        if (xray || world == null) {
            if (outHit != null) {
                outHit.clear();
            }
            return false;
        }
        return world.rayCast(this, x, y, targetX, targetY, outHit);
    }

    protected final RayCastHit rayCast(float targetX, float targetY) {
        RayCastWorld world = rayHandler.getRayCastWorld();
        if (xray || world == null) {
            return null;
        }
        return world.rayCast(this, x, y, targetX, targetY);
    }

    protected final List<LightOccluder> getLightOccluders() {
        RayCastWorld world = rayHandler.getRayCastWorld();
        return (world == null) ? Collections.emptyList() : world.getLightOccluders();
    }

    public RayHandler getRayHandler() {
        return rayHandler;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        if (color == null) throw new NullPointerException("color cannot be null");
        this.color.set(color);
    }

    public void setColor(float r, float g, float b, float a) {
        this.color.set(r, g, b, a);
    }

    public int getRays() {
        return rays;
    }

    public void setRays(int rays) {
        if (rays < 3) throw new IllegalArgumentException("rays must be >= 3");
        if (this.rays == rays) return;

        this.rays = rays;
        ensureRayCapacity(rays);
        dirty = true;
    }

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = distance;
        dirty = true;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
        dirty = true;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
        dirty = true;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        dirty = true;
    }

    public int getCategoryBits() {
        return categoryBits;
    }

    public void setCategoryBits(int categoryBits) {
        this.categoryBits = categoryBits;
    }

    public int getOcclusionMaskBits() {
        return occlusionMaskBits;
    }

    public void setOcclusionMaskBits(int occlusionMaskBits) {
        if (this.occlusionMaskBits == occlusionMaskBits) return;
        this.occlusionMaskBits = occlusionMaskBits;
        dirty = true;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isXray() {
        return xray;
    }

    public void setXray(boolean xray) {
        this.xray = xray;
        dirty = true;
    }

    public boolean isSoft() {
        return soft;
    }

    public void setSoft(boolean soft) {
        this.soft = soft;
    }

    public float getSoftnessLength() {
        return softnessLength;
    }

    public void setSoftnessLength(float softnessLength) {
        this.softnessLength = softnessLength;
    }

    public float[] getEndX() {
        return endX;
    }

    public float[] getEndY() {
        return endY;
    }

    public float[] getFractions() {
        return fractions;
    }

    protected final void ensureRayCapacity(int count) {
        if (endX != null && endX.length == count) {
            return;
        }

        endX = new float[count];
        endY = new float[count];
        fractions = new float[count];
        for (int i = 0; i < count; i++) {
            fractions[i] = 1f;
        }
    }

    public boolean isDirty() {
        return dirty;
    }
}
