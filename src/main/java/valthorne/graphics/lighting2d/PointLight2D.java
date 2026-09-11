package valthorne.graphics.lighting2d;

import valthorne.graphics.Color;

/**
 * Mutable point or cone light for the XY world plane used by {@link Lighting2D}.
 * Position, influence radius and source radius use world units; cone direction
 * and half angles use radians. RGB emission is linear and independent of alpha.
 * This object stores configuration and cache revisions, not GPU resources.
 *
 * <p>Defaults describe an enabled white omnidirectional light at the origin:
 * influence radius 250, intensity 2, source radius 5, shadows enabled and all
 * occluder categories accepted. Both cone half angles initially equal PI.</p>
 *
 * <pre>{@code
 * PointLight2D light = new PointLight2D()
 *         .setPosition(100, 80)
 *         .setRadius(200)
 *         .setIntensity(3)
 *         .setCone(0, 0.3f, 0.6f);
 * lighting.addLight(light);
 * }</pre>
 *
 * <p>Setters validate before changing state. Geometry-related changes also advance
 * the shadow revision; appearance changes advance only the general revision.
 * Color and cone assignments always advance that revision, even for equal inputs.
 * Use setters rather than changing package fields so rendering caches can detect
 * updates. Access is not synchronized; avoid mutation during lighting updates.</p>
 *
 * @author Albert Beaupre
 */
public final class PointLight2D {
    float x, y, radius = 250, r = 1, g = 1, b = 1, intensity = 2, sourceRadius = 5, direction, inner = (float) Math.PI, outer = (float) Math.PI; // XY position, influence radius, linear RGB, intensity, source radius and cone angles.
    int mask = -1; // Accepted occluder category bits, initially all categories.
    boolean shadows = true, enabled = true; // Shadow casting and participation in lighting, both initially enabled.
    long revision, shadowRevision; // General configuration and shadow-geometry change counters.

    /**
     * Rejects NaN and infinity before a scalar enters light configuration.
     *
     * @param x the candidate scalar
     * @return the unchanged finite value
     * @throws IllegalArgumentException if x is non-finite
     */
    static float finite(float x) {
        if (!Float.isFinite(x)) throw new IllegalArgumentException("Value must be finite");
        return x;
    }

    /**
     * Requires a finite scalar strictly above zero, rejecting either signed zero.
     *
     * @param x the candidate positive setting
     * @throws IllegalArgumentException if x is non-finite or not positive
     */
    static void positive(float x) {if (finite(x) <= 0) throw new IllegalArgumentException("Value must be positive");}

    /**
     * Requires a finite scalar at least zero; either signed zero is accepted.
     *
     * @param x the candidate nonnegative setting
     * @throws IllegalArgumentException if x is non-finite or negative
     */
    static void nonnegative(float x) {
        if (finite(x) < 0) throw new IllegalArgumentException("Value must be nonnegative");
    }

    /**
     * Reads the horizontal world position without changing revision counters.
     *
     * @return the light center's X coordinate in world units
     */
    public float getX() {return x;}

    /**
     * Reads the vertical coordinate within the XY world plane.
     *
     * @return the light center's Y coordinate in world units
     */
    public float getY() {return y;}

    /**
     * Returns the influence radius used for lighting coverage and occluder queries.
     * This is separate from the source radius used for soft-shadow appearance.
     *
     * @return the positive influence radius in world units
     */
    public float getRadius() {return radius;}

    /**
     * Changes the light's influence radius. A changed radius advances both
     * revision counters so shadow geometry is reconsidered for the new extent.
     *
     * @param radius the finite positive radius in world units
     * @return this light for chaining
     * @throws IllegalArgumentException if radius is non-finite or not positive
     */
    public PointLight2D setRadius(float radius) {
        positive(radius);
        if (this.radius != radius) {
            this.radius = radius;
            revision++;
            shadowRevision++;
        }
        return this;
    }

    /**
     * Moves the light center and invalidates lighting and shadow geometry when
     * either coordinate changes. Both inputs are checked before any assignment.
     *
     * @param x the finite world X coordinate
     * @param y the finite world Y coordinate
     * @return this light for chaining
     * @throws IllegalArgumentException if either coordinate is non-finite
     */
    public PointLight2D setPosition(float x, float y) {
        finite(x);
        finite(y);
        if (this.x != x || this.y != y) {
            this.x = x;
            this.y = y;
            revision++;
            shadowRevision++;
        }
        return this;
    }

    /**
     * Copies linear RGB emission components, ignoring alpha. Components may exceed
     * one but must be finite and nonnegative. The Color reference is not retained;
     * every successful assignment advances the general revision only.
     *
     * @param c the source color with valid RGB components
     * @return this light for chaining
     * @throws NullPointerException     if c is null
     * @throws IllegalArgumentException if an RGB component is non-finite or negative
     */
    public PointLight2D setColor(Color c) {
        nonnegative(c.r());
        nonnegative(c.g());
        nonnegative(c.b());
        r = c.r();
        g = c.g();
        b = c.b();
        revision++;
        return this;
    }

    /**
     * Sets the nonnegative emission multiplier. Zero removes its emitted intensity
     * without changing the enabled flag. A changed value advances general revision.
     *
     * @param value the finite nonnegative intensity
     * @return this light for chaining
     * @throws IllegalArgumentException if value is non-finite or negative
     */
    public PointLight2D setIntensity(float value) {
        nonnegative(value);
        if (intensity != value) {
            intensity = value;
            revision++;
        }
        return this;
    }

    /**
     * Sets the source size used by lighting to control shadow softness, independently
     * of influence radius. Zero is permitted. Changes affect appearance revision
     * without rebuilding the cached occluder geometry.
     *
     * @param value the finite nonnegative source radius in world units
     * @return this light for chaining
     * @throws IllegalArgumentException if value is non-finite or negative
     */
    public PointLight2D setSourceRadius(float value) {
        nonnegative(value);
        if (sourceRadius != value) {
            sourceRadius = value;
            revision++;
        }
        return this;
    }

    /**
     * Assigns cone direction and half angles. The inner angle marks full cone
     * contribution and the outer angle bounds its falloff; equal angles produce
     * a hard edge. Setting both to PI produces omnidirectional coverage. Direction
     * is stored without wrapping, and every successful call advances general revision.
     *
     * @param direction the finite cone direction in radians in the XY plane
     * @param inner     the finite nonnegative inner half angle in radians
     * @param outer     the finite positive outer half angle, at most PI
     * @return this light for chaining
     * @throws IllegalArgumentException if inputs are non-finite, inner is negative,
     *                                  outer is not positive, or inner exceeds outer or outer exceeds PI
     */
    public PointLight2D setCone(float direction, float inner, float outer) {
        finite(direction);
        nonnegative(inner);
        positive(outer);
        if (inner > outer || outer > (float) Math.PI)
            throw new IllegalArgumentException("Cone requires 0 <= inner <= outer <= PI");
        this.direction = direction;
        this.inner = inner;
        this.outer = outer;
        revision++;
        return this;
    }

    /**
     * Enables or disables occluder shadows while retaining the light's emission
     * settings. A changed flag advances both general and shadow revisions.
     *
     * @param enabled whether this light should cast occluder shadows
     * @return this light for chaining
     */
    public PointLight2D setCastsShadows(boolean enabled) {
        if (shadows != enabled) {
            shadows = enabled;
            revision++;
            shadowRevision++;
        }
        return this;
    }

    /**
     * Controls participation in lighting without discarding configuration.
     * Changing this flag advances only the general revision.
     *
     * @param enabled whether the renderer should consider this light
     * @return this light for chaining
     */
    public PointLight2D setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            revision++;
        }
        return this;
    }

    /**
     * Selects occluder categories by bit intersection. An occluder participates
     * when its category shares any bit with this mask; zero excludes all categories
     * and -1 accepts all. Changes invalidate both revision counters.
     *
     * @param mask the accepted category bitmask, with all integer values permitted
     * @return this light for chaining
     */
    public PointLight2D setOcclusionMask(int mask) {
        if (this.mask != mask) {
            this.mask = mask;
            revision++;
            shadowRevision++;
        }
        return this;
    }
}
