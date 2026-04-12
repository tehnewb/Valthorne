package valthorne.graphics.lighting;

import valthorne.graphics.Color;
import valthorne.math.geometry.Shape;

/**
 * Shape-backed scene element for radiance-cascade GI input.
 *
 * <p>The scene buffer encoding is:
 * <ul>
 *   <li>RGB = emissive radiance</li>
 *   <li>A   = transmittance (1 = open air, 0 = solid occluder)</li>
 * </ul>
 *
 * <p>{@code additive=true} is useful for emitter halos so their radiance can
 * accumulate while preserving the existing transmittance written to the buffer.
 */
public record GISceneShape(Shape shape, Color emission, float transmittance, boolean additive) {

    public GISceneShape {
        if (shape == null) throw new NullPointerException("shape cannot be null");
        if (emission == null) throw new NullPointerException("emission cannot be null");
        if (transmittance < 0f || transmittance > 1f) {
            throw new IllegalArgumentException("transmittance must be in [0, 1]");
        }
    }

    public static GISceneShape occluder(Shape shape) {
        return new GISceneShape(shape, new Color(0f, 0f, 0f, 0f), 0f, false);
    }

    public static GISceneShape emitter(Shape shape, Color emission) {
        return new GISceneShape(shape, emission, 1f, true);
    }

    public static GISceneShape surface(Shape shape, Color emission, float transmittance) {
        return new GISceneShape(shape, emission, transmittance, false);
    }
}
