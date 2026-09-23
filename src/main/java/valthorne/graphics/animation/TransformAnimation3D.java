package valthorne.graphics.animation;

import valthorne.graphics.scene.ModelInstance3D;
import valthorne.graphics.scene.SceneNode3D;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Samples immutable local-transform keyframes using linear position and scale
 * interpolation and shortest-path quaternion interpolation. Time is measured in
 * seconds. Sampling can clamp to the last frame or wrap over the animation duration;
 * this class stores no playback clock and never advances time on its own.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * TransformAnimation3D animation = new TransformAnimation3D(
 *         new TransformAnimation3D.Keyframe(0, new Vector3f(),
 *                 new Quaternionf(), new Vector3f(1, 1, 1)),
 *         new TransformAnimation3D.Keyframe(2, new Vector3f(3, 0, 0),
 *                 new Quaternionf(), new Vector3f(2, 2, 2)));
 * animation.apply(instance, elapsedSeconds, true);
 * }</pre>
 *
 * <p>Frames must start at zero and increase strictly. Each scale component must
 * retain its sign between adjacent frames, preventing an intended interpolation
 * path through a zero scale. Negative scale is allowed when that sign is consistent.</p>
 *
 * <p>The animation clones the frame array and keyframes protect their mutable
 * components with defensive copies. Sampling allocates a new keyframe rather
 * than exposing stored data. Applying a sample changes a target's local transform
 * while preserving its model, parent transform or hierarchy; target ownership
 * remains with the caller.</p>
 *
 * @author Albert Beaupre
 */
public final class TransformAnimation3D {
    private final Keyframe[] frames; // Cloned ordered array of immutable frame snapshots.

    /**
     * Creates an animation from at least two frames, cloning the supplied array.
     * Frame zero must be at time zero; subsequent times must increase strictly.
     * Corresponding scale components must have matching signs across each pair.
     * These rules yield a positive duration and nonsingular intended scale paths.
     *
     * @param frames the ordered nonnull frames beginning at time zero
     * @throws NullPointerException     if the array or a frame is null
     * @throws IllegalArgumentException if there are fewer than two frames, times
     *                                  are invalidly ordered, or scale signs change
     */
    public TransformAnimation3D(Keyframe... frames) {
        if (frames.length < 2) throw new IllegalArgumentException("At least two keyframes are required");
        this.frames = frames.clone();
        if (frames[0].time != 0) throw new IllegalArgumentException("First keyframe must be at time zero");
        for (int i = 1; i < frames.length; i++) {
            if (frames[i].time <= frames[i - 1].time)
                throw new IllegalArgumentException("Keyframe times must increase");
            Vector3f a = frames[i - 1].scale, b = frames[i].scale;
            if (Math.signum(a.x()) != Math.signum(b.x()) || Math.signum(a.y()) != Math.signum(b.y()) || Math.signum(a.z()) != Math.signum(b.z()))
                throw new IllegalArgumentException("Interpolated scale cannot cross zero");
        }
    }

    /**
     * Returns the final frame's timestamp, which is the positive animation
     * duration because the first frame starts at zero.
     *
     * @return the duration in seconds
     */
    public float getDuration() {
        return frames[frames.length - 1].time;
    }

    /**
     * Samples the requested time and replaces a model instance's local position,
     * scale and quaternion orientation. The instance's model, material and parent
     * transform are preserved. Sampling finishes before target mutation begins.
     *
     * @param target the nonnull instance to animate
     * @param time   the finite nonnegative playback time in seconds
     * @param loop   whether time wraps at duration instead of clamping
     * @throws IllegalArgumentException if sampling rejects time or its result
     * @throws NullPointerException     if target is null after sampling succeeds
     */
    public void apply(ModelInstance3D target, float time, boolean loop) {
        Keyframe frame = sample(time, loop);
        target.setPosition(frame.position).setScale(frame.scale.x(), frame.scale.y(), frame.scale.z()).setRotation(frame.rotation);
    }

    /**
     * Samples the requested time and replaces a node's local position, scale and
     * quaternion orientation. Parent and child relationships remain intact, so
     * hierarchy transforms continue to compose with the sampled local transform.
     *
     * @param target the nonnull hierarchy node to animate
     * @param time   the finite nonnegative playback time in seconds
     * @param loop   whether time wraps at duration instead of clamping
     * @throws IllegalArgumentException if sampling rejects time or its result
     * @throws NullPointerException     if target is null after sampling succeeds
     */
    public void apply(SceneNode3D target, float time, boolean loop) {
        Keyframe frame = sample(time, loop);
        target.setPosition(frame.position.x(), frame.position.y(), frame.position.z()).setScale(frame.scale.x(), frame.scale.y(), frame.scale.z()).setRotation(frame.rotation);
    }

    /**
     * Returns an interpolated transform at the effective playback time. Looping
     * uses remainder by duration, so an exact duration multiple samples time zero.
     * Nonlooping playback clamps later times to the last frame. Negative time is
     * rejected rather than wrapped backward.
     *
     * <p>A binary search selects adjacent frames. Position and scale interpolate
     * linearly, and orientation uses quaternion SLERP. The returned keyframe's
     * time is the wrapped or clamped time, not the original argument. Even exact
     * endpoint samples create fresh component storage.</p>
     *
     * @param time the finite nonnegative requested time in seconds
     * @param loop whether to repeat the animation at its duration
     * @return a newly allocated immutable transform snapshot
     * @throws IllegalArgumentException if time is negative or non-finite, or
     *                                  interpolation produces an invalid transform
     */
    public Keyframe sample(float time, boolean loop) {
        if (!Float.isFinite(time) || time < 0)
            throw new IllegalArgumentException("Time must be finite and nonnegative");
        float t = loop ? time % getDuration() : Math.min(time, getDuration());
        int low = 0, high = frames.length - 1;
        while (high - low > 1) {
            int mid = (low + high) / 2;
            if (frames[mid].time <= t) low = mid;
            else high = mid;
        }
        Keyframe a = frames[low], b = frames[high];
        float alpha = (t - a.time) / (b.time - a.time);
        return new Keyframe(t, new Vector3f(a.position).lerp(b.position, alpha), new Quaternionf(a.rotation).slerp(b.rotation, alpha), new Vector3f(a.scale).lerp(b.scale, alpha));
    }

    /**
     * Immutable transform sample at a nonnegative time. Position, rotation and
     * scale are copied on construction and access. Position and scale must be
     * finite, with every scale component nonzero. Rotation must be finite and
     * nonzero and is normalized while copying the supplied quaternion.
     *
     * @param time     the finite time in seconds
     * @param position the local-space position to snapshot
     * @param rotation the local orientation to snapshot
     * @param scale    the finite nonzero per-axis scale to snapshot
     * @author Albert Beaupre
     */
    public record Keyframe(float time, Vector3f position, Quaternionf rotation, Vector3f scale) {
        /**
         * Validates time, copies transform components, and rejects non-finite
         * position/scale or zero scale components. No caller-owned value is modified.
         *
         * @param time     the finite nonnegative sample time in seconds
         * @param position the nonnull local position
         * @param rotation the nonnull orientation
         * @param scale    the nonnull per-axis scale
         * @throws IllegalArgumentException if time is invalid, position or scale is
         *                                  non-finite, or any scale component is zero
         * @throws NullPointerException     if a transform component object is null
         */
        public Keyframe {
            if (!Float.isFinite(time) || time < 0)
                throw new IllegalArgumentException("Time must be finite and nonnegative");
            position = new Vector3f(position);
            double rotationLength = Math.sqrt((double) rotation.x() * rotation.x() + (double) rotation.y() * rotation.y() + (double) rotation.z() * rotation.z() + (double) rotation.w() * rotation.w());
            if (!Double.isFinite(rotationLength) || rotationLength == 0)
                throw new IllegalArgumentException("Quaternion must be finite and nonzero");
            rotation = new Quaternionf((float) (rotation.x() / rotationLength), (float) (rotation.y() / rotationLength), (float) (rotation.z() / rotationLength), (float) (rotation.w() / rotationLength));
            scale = new Vector3f(scale);
            for (float v : new float[]{position.x(), position.y(), position.z(), scale.x(), scale.y(), scale.z()})
                if (!Float.isFinite(v)) throw new IllegalArgumentException("Transform must be finite");
            if (scale.x() == 0 || scale.y() == 0 || scale.z() == 0)
                throw new IllegalArgumentException("Scale must be nonzero");
        }

        /**
         * Returns an independent local-position vector. Editing the result does
         * not change the stored frame or any animation that references it.
         *
         * @return a newly allocated copy of local position
         */
        @Override
        public Vector3f position() {
            return new Vector3f(position);
        }

        /**
         * Returns an independent orientation copy without renormalizing it.
         * Callers can modify the returned quaternion without altering this frame.
         *
         * @return a newly allocated orientation copy
         */
        @Override
        public Quaternionf rotation() {
            return new Quaternionf(rotation);
        }

        /**
         * Returns an independent scale vector, preserving signed components.
         * Changing the result cannot affect stored interpolation endpoints.
         *
         * @return a newly allocated copy of local scale
         */
        @Override
        public Vector3f scale() {
            return new Vector3f(scale);
        }
    }
}
