package valthorne.graphics.lighting;

import valthorne.graphics.Color;

public abstract class Light {

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
    protected float[] segments;
    protected boolean dirty = true;

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

        this.endX = new float[rays];
        this.endY = new float[rays];
        this.fractions = new float[rays];
        this.segments = new float[rays * 18];

        for (int i = 0; i < rays; i++) {
            fractions[i] = 1f;
        }
    }

    public abstract void update();

    protected abstract void computeRayEnd(int index, float[] output);

    protected void rebuild() {
        float[] temp = new float[2];

        for (int i = 0; i < rays; i++) {
            computeRayEnd(i, temp);

            float targetX = temp[0];
            float targetY = temp[1];

            if (xray || rayHandler.getRayCastWorld() == null) {
                endX[i] = targetX;
                endY[i] = targetY;
                fractions[i] = 1f;
            } else {
                RayCastHit hit = rayHandler.getRayCastWorld().rayCast(x, y, targetX, targetY);
                if (hit != null && hit.isHit()) {
                    endX[i] = hit.getX();
                    endY[i] = hit.getY();
                    fractions[i] = hit.getFraction();
                } else {
                    endX[i] = targetX;
                    endY[i] = targetY;
                    fractions[i] = 1f;
                }
            }
        }

        sortEndpointsByAngle();
        buildSegments();
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

    protected void buildSegments() {
        int idx = 0;

        float r = color.r();
        float g = color.g();
        float b = color.b();
        float a = color.a();

        for (int i = 0; i < endX.length; i++) {

            int next = i + 1;
            if (next == endX.length) next = 0;

            segments[idx++] = x;
            segments[idx++] = y;
            segments[idx++] = r;
            segments[idx++] = g;
            segments[idx++] = b;
            segments[idx++] = a;

            segments[idx++] = endX[i];
            segments[idx++] = endY[i];
            segments[idx++] = r;
            segments[idx++] = g;
            segments[idx++] = b;
            segments[idx++] = a;

            segments[idx++] = endX[next];
            segments[idx++] = endY[next];
            segments[idx++] = r;
            segments[idx++] = g;
            segments[idx++] = b;
            segments[idx++] = a;
        }
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
        dirty = true;
    }

    public void setColor(float r, float g, float b, float a) {
        this.color.set(r, g, b, a);
        dirty = true;
    }

    public int getRays() {
        return rays;
    }

    public void setRays(int rays) {
        if (rays < 3) throw new IllegalArgumentException("rays must be >= 3");
        if (this.rays == rays) return;

        this.rays = rays;
        this.endX = new float[rays];
        this.endY = new float[rays];
        this.fractions = new float[rays];
        this.segments = new float[rays * 18];

        for (int i = 0; i < rays; i++) {
            fractions[i] = 1f;
        }

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
        dirty = true;
    }

    public float getSoftnessLength() {
        return softnessLength;
    }

    public void setSoftnessLength(float softnessLength) {
        this.softnessLength = softnessLength;
        dirty = true;
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

    public float[] getSegments() {
        return segments;
    }

    public boolean isDirty() {
        return dirty;
    }
}