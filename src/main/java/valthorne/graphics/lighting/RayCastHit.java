package valthorne.graphics.lighting;

public final class RayCastHit {

    private boolean hit;
    private float x;
    private float y;
    private float fraction = 1f;
    private Object collider;

    public RayCastHit() {
    }

    public RayCastHit(float x, float y) {
        this.hit = true;
        this.x = x;
        this.y = y;
    }

    public boolean isHit() {
        return hit;
    }

    public void setHit(boolean hit) {
        this.hit = hit;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getFraction() {
        return fraction;
    }

    public void setFraction(float fraction) {
        this.fraction = fraction;
    }

    public Object getCollider() {
        return collider;
    }

    public void setCollider(Object collider) {
        this.collider = collider;
    }

    public void set(boolean hit, float x, float y, float fraction, Object collider) {
        this.hit = hit;
        this.x = x;
        this.y = y;
        this.fraction = fraction;
        this.collider = collider;
    }

    public void clear() {
        this.hit = false;
        this.x = 0f;
        this.y = 0f;
        this.fraction = 1f;
        this.collider = null;
    }
}