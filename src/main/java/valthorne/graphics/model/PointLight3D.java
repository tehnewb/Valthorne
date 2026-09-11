package valthorne.graphics.model;

import valthorne.graphics.Color;
import org.joml.Vector3f;

/**
 * Mutable world-space point-light parameters consumed by the 3D lighting system.
 * A new light is white at the origin, with a range of ten world units and an
 * intensity multiplier of one, and shadow casting disabled. Filament interprets
 * each intensity unit as 1000 lumens and range as its finite falloff radius.
 * This object stores parameters only; it allocates
 * no GPU resources and does not register itself with a scene or render state.
 *
 * <p>Position and color getters expose live mutable objects. Setters copy color
 * components and write position components into those same objects. Range must
 * be finite and positive, and intensity finite and nonnegative. Position and
 * color setters perform no equivalent finiteness validation here; consumers such
 * as the light grid validate packed lighting values before use.</p>
 *
 * <p>The tiled lighting path uses position, range, RGB and intensity; color alpha
 * is not part of that packed light data. Zero intensity disables contribution
 * without removing the light from its owner's list. Coordinate units should match
 * scene geometry. Avoid mutating a light concurrently with lighting submission.</p>
 *
 * @author Albert Beaupre
 * @see MeshRenderState3D#addLight(PointLight3D)
 */
public final class PointLight3D {
    private final Vector3f position = new Vector3f(); // Live world-space light position, initially the origin.
    private final Color color = Color.WHITE.copy(); // Live light color, independent of the shared white constant.
    private float range = 10f, intensity = 1f; // Positive world-space range and nonnegative brightness multiplier.
    private boolean castsShadows; // Whether Filament should allocate shadow maps for this light.

    /**
     * Reads whether Filament should allocate shadow maps for this light. This flag
     * is not included in the tiled forward-lighting packed data.
     *
     * @return requested shadow-map participation, initially false
     */
    public boolean isCastsShadows() {return castsShadows;}

    /**
     * Sets optional Filament shadow participation without allocating resources here.
     * The owning renderer applies the flag when synchronizing light state.
     *
     * @param enabled whether this light requests shadow maps
     * @return this light
     */
    public PointLight3D setCastsShadows(boolean enabled) {
        castsShadows = enabled;
        return this;
    }

    /**
     * Returns the internal position vector. Mutations affect future light reads
     * directly; copy the vector to retain an independent position snapshot.
     *
     * @return the live world-space position
     */
    public Vector3f getPosition() {return position;}

    /**
     * Returns the internal mutable color. Changing it updates subsequent lighting
     * input without another setter call; this is not a defensive copy.
     *
     * @return the live light color
     */
    public Color getColor() {return color;}

    /**
     * Copies color components into this light without retaining the supplied
     * color object. Use finite nonnegative RGB values for the tiled light grid;
     * this setter does not apply those validation rules itself.
     *
     * @param color the color whose components should be copied
     * @return this light for chaining
     * @throws NullPointerException if color is null
     */
    public PointLight3D setColor(Color color) {
        this.color.set(color);
        return this;
    }

    /**
     * Returns the configured finite influence range used by lighting consumers
     * for attenuation and conservative light-volume culling.
     *
     * @return the positive range in world units, initially ten
     */
    public float getRange() {return range;}

    /**
     * Replaces the influence range after validating that it is finite and strictly
     * positive. Rejected values leave the previous range unchanged.
     *
     * @param range the desired influence range in world units
     * @return this light for chaining
     * @throws IllegalArgumentException if range is non-finite, zero or negative
     */
    public PointLight3D setRange(float range) {
        if (!Float.isFinite(range) || range <= 0f)
            throw new IllegalArgumentException("Range must be finite and positive");
        this.range = range;
        return this;
    }

    /**
     * Returns the nonnegative brightness multiplier. A value of zero produces
     * no contribution in the tiled lighting path while retaining the light object.
     *
     * @return the finite intensity multiplier, initially one
     */
    public float getIntensity() {return intensity;}

    /**
     * Replaces the brightness multiplier after finite, nonnegative validation.
     * Zero is accepted, and there is no upper cap on finite positive values.
     *
     * @param intensity the requested brightness multiplier
     * @return this light for chaining
     * @throws IllegalArgumentException if intensity is negative or non-finite
     */
    public PointLight3D setIntensity(float intensity) {
        if (!Float.isFinite(intensity) || intensity < 0f)
            throw new IllegalArgumentException("Intensity must be finite and nonnegative");
        this.intensity = intensity;
        return this;
    }

    /**
     * Writes the world-space position into the existing internal vector. Supply
     * finite coordinates; this setter does not reject non-finite values itself.
     *
     * @param x the world X coordinate
     * @param y the world Y coordinate
     * @param z the world Z coordinate
     * @return this light for chaining
     */
    public PointLight3D setPosition(float x, float y, float z) {
        position.set(x, y, z);
        return this;
    }
}
