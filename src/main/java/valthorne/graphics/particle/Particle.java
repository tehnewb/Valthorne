package valthorne.graphics.particle;

import valthorne.graphics.Color;
import valthorne.io.pool.Poolable;

/**
 * A pooled particle state container used by {@link ParticleSystem} during CPU simulation.
 *
 * <p>This class is intentionally a simple <b>mutable data object</b>. It stores all per-particle runtime state
 * so the particle system can update and render particles without allocating new objects.</p>
 *
 * <h2>What this class stores</h2>
 * <ul>
 *     <li><b>Lifecycle</b>: active flag, age, and total life duration</li>
 *     <li><b>Motion</b>: position and velocity</li>
 *     <li><b>Transform</b>: rotation and rotation speed</li>
 *     <li><b>Appearance</b>: start/end colors and current interpolated color</li>
 *     <li><b>Scaling</b>: start/end scale endpoints and current interpolated scale</li>
 * </ul>
 *
 * <h2>Pooling rules</h2>
 * <ul>
 *     <li>This class implements {@link Poolable}. The pool should call {@link #reset()} before reuse.</li>
 *     <li>{@link Color} instances are stored as final fields to avoid allocating colors per particle or per frame.</li>
 *     <li>{@link #reset()} restores deterministic defaults so the next spawn starts from a known state.</li>
 * </ul>
 *
 * <h2>Coordinate and unit conventions</h2>
 * <ul>
 *     <li>Position is world-space in your engine's units.</li>
 *     <li>Velocity is units/second.</li>
 *     <li>Rotation is degrees, rotationSpeed is degrees/second.</li>
 * </ul>
 *
 * @author Albert Beaupre
 * @since February 10th, 2026
 */
public class Particle implements Poolable {

    private boolean active;                                    // Whether this particle is currently alive and should be updated/drawn.
    private float x, y;                                        // Current world-space particle position.
    private float velX, velY;                                  // Current world-space particle velocity (units per second).
    private float age;                                         // Current age in seconds since spawn.
    private float life;                                        // Total lifetime in seconds before the particle dies.
    private float rotation;                                    // Current rotation angle in degrees.
    private float rotationSpeed;                               // Rotation speed in degrees per second.
    private float startScale;                                  // Scale at spawn time (t = 0).
    private float endScale;                                    // Scale at death time (t = 1).
    private float scale;                                       // Current interpolated scale.
    private final Color startColor = new Color(1f, 1f, 1f, 1f); // Color at spawn time (t = 0).
    private final Color endColor = new Color(1f, 1f, 1f, 0f);   // Color at death time (t = 1).
    private final Color color = new Color(1f, 1f, 1f, 1f);      // Current interpolated color used for rendering.

    /**
     * Resets this particle to deterministic defaults so it can be reused by a pool.
     *
     * <p>This method is expected to be called by your {@link valthorne.io.pool.Pool} implementation
     * (or by {@link ParticleSystem}) before initializing a particle for a new spawn.</p>
     *
     * <p>Defaults:</p>
     * <ul>
     *     <li>inactive, position/velocity/rotation = 0</li>
     *     <li>life = 0, age = 0</li>
     *     <li>startScale = 1, endScale = 0, current scale = 1</li>
     *     <li>startColor = white (1,1,1,1), endColor = transparent white (1,1,1,0), current color = white</li>
     * </ul>
     */
    @Override
    public void reset() {
        active = false;

        x = y = 0f;
        velX = velY = 0f;

        age = 0f;
        life = 0f;

        rotation = 0f;
        rotationSpeed = 0f;

        startScale = 1f;
        endScale = 0f;
        scale = 1f;

        startColor.set(1f, 1f, 1f, 1f);
        endColor.set(1f, 1f, 1f, 0f);
        color.set(1f, 1f, 1f, 1f);
    }

    /**
     * Returns whether this particle is currently active (alive).
     *
     * @return true if active, false if inactive
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Sets whether this particle is currently active (alive).
     *
     * @param active true to mark alive, false to mark inactive
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Returns the particle's world-space x position.
     *
     * @return x position
     */
    public float getX() {
        return x;
    }

    /**
     * Sets the particle's world-space x position.
     *
     * @param x new x position
     */
    public void setX(float x) {
        this.x = x;
    }

    /**
     * Returns the particle's world-space y position.
     *
     * @return y position
     */
    public float getY() {
        return y;
    }

    /**
     * Sets the particle's world-space y position.
     *
     * @param y new y position
     */
    public void setY(float y) {
        this.y = y;
    }

    /**
     * Returns the particle's x velocity component.
     *
     * @return x velocity
     */
    public float getVelX() {
        return velX;
    }

    /**
     * Sets the particle's x velocity component.
     *
     * @param velX new x velocity
     */
    public void setVelX(float velX) {
        this.velX = velX;
    }

    /**
     * Returns the particle's y velocity component.
     *
     * @return y velocity
     */
    public float getVelY() {
        return velY;
    }

    /**
     * Sets the particle's y velocity component.
     *
     * @param velY new y velocity
     */
    public void setVelY(float velY) {
        this.velY = velY;
    }

    /**
     * Returns the particle's current age in seconds since spawn.
     *
     * @return age in seconds
     */
    public float getAge() {
        return age;
    }

    /**
     * Sets the particle's current age in seconds since spawn.
     *
     * @param age new age in seconds
     */
    public void setAge(float age) {
        this.age = age;
    }

    /**
     * Returns the particle's total lifetime in seconds.
     *
     * @return lifetime in seconds
     */
    public float getLife() {
        return life;
    }

    /**
     * Sets the particle's total lifetime in seconds.
     *
     * @param life new lifetime in seconds
     */
    public void setLife(float life) {
        this.life = life;
    }

    /**
     * Returns the particle's current rotation in degrees.
     *
     * @return rotation in degrees
     */
    public float getRotation() {
        return rotation;
    }

    /**
     * Sets the particle's current rotation in degrees.
     *
     * @param rotation new rotation in degrees
     */
    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    /**
     * Returns the particle's rotation speed in degrees per second.
     *
     * @return rotation speed in degrees/sec
     */
    public float getRotationSpeed() {
        return rotationSpeed;
    }

    /**
     * Sets the particle's rotation speed in degrees per second.
     *
     * @param rotationSpeed new rotation speed in degrees/sec
     */
    public void setRotationSpeed(float rotationSpeed) {
        this.rotationSpeed = rotationSpeed;
    }

    /**
     * Returns the scale at spawn time (t = 0).
     *
     * @return start scale
     */
    public float getStartScale() {
        return startScale;
    }

    /**
     * Sets the scale at spawn time (t = 0).
     *
     * @param startScale start scale
     */
    public void setStartScale(float startScale) {
        this.startScale = startScale;
    }

    /**
     * Returns the scale at death time (t = 1).
     *
     * @return end scale
     */
    public float getEndScale() {
        return endScale;
    }

    /**
     * Sets the scale at death time (t = 1).
     *
     * @param endScale end scale
     */
    public void setEndScale(float endScale) {
        this.endScale = endScale;
    }

    /**
     * Returns the current interpolated scale.
     *
     * @return current scale
     */
    public float getScale() {
        return scale;
    }

    /**
     * Sets the current interpolated scale.
     *
     * @param scale current scale
     */
    public void setScale(float scale) {
        this.scale = scale;
    }

    /**
     * Returns the color at spawn time (t = 0).
     *
     * @return start color reference (mutable, reused)
     */
    public Color getStartColor() {
        return startColor;
    }

    /**
     * Returns the color at death time (t = 1).
     *
     * @return end color reference (mutable, reused)
     */
    public Color getEndColor() {
        return endColor;
    }

    /**
     * Returns the current interpolated color used for rendering.
     *
     * @return current color reference (mutable, reused)
     */
    public Color getColor() {
        return color;
    }
}