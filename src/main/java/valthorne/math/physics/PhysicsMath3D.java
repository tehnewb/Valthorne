package valthorne.math.physics;

import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.Objects;

/**
 * Internal numeric validation and value conversion for the Jolt physics boundary.
 * Scalar helpers reject non-finite values and optionally require positive or
 * nonnegative inputs. Vector validation returns the supplied engine vector;
 * conversion helpers instead construct separate engine or Jolt value objects.
 *
 * <p>Conversions preserve component order and do not change axes or units.
 * Engine positions use floats, so reading a native real-valued position narrows
 * its components to float precision. Quaternion conversion in either direction
 * validates and normalizes components before crossing the physics boundary.</p>
 *
 * <p>This utility has no shared mutable state. It does not manage body lifetime,
 * synchronize access to borrowed native objects, or perform world-thread checks;
 * callers must honor those requirements before reading native values.</p>
 *
 * @author Albert Beaupre
 */
final class PhysicsMath3D {
    /**
     * Prevents construction of this stateless conversion utility. All operations
     * work on explicit arguments and return validated or newly converted values.
     */
    private PhysicsMath3D() {
    }

    /**
     * Rejects NaN and positive or negative infinity before a numeric input crosses
     * the physics boundary. Finite values, including signed zero, are unchanged.
     *
     * @param value the candidate scalar
     * @param name  the setting name used in an error message
     * @return the same finite scalar
     * @throws IllegalArgumentException if value is non-finite
     */
    static float finite(float value, String name) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return value;
    }

    /**
     * Validates that a scalar is finite and strictly greater than zero.
     * Both signed zeros and all negative inputs are rejected.
     *
     * @param value the candidate scalar
     * @param name  the setting name used in validation messages
     * @return the unchanged positive scalar
     * @throws IllegalArgumentException if value is non-finite or not positive
     */
    static float positive(float value, String name) {
        if (finite(value, name) <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    /**
     * Validates a finite scalar whose lower bound is zero. Signed zero is accepted
     * and retained; negative finite values are rejected after finiteness validation.
     *
     * @param value the candidate scalar
     * @param name  the setting name used in validation messages
     * @return the unchanged nonnegative scalar
     * @throws IllegalArgumentException if value is negative or non-finite
     */
    static float nonnegative(float value, String name) {
        if (finite(value, name) < 0) throw new IllegalArgumentException(name + " must be nonnegative");
        return value;
    }

    /**
     * Checks that an engine vector exists and that each component is finite.
     * Does not normalize, copy or mutate the vector; callers that retain a
     * snapshot must copy the returned reference themselves.
     *
     * @param value the engine vector to validate
     * @return the same validated vector instance
     * @throws NullPointerException     if value is null
     * @throws IllegalArgumentException if any component is non-finite
     */
    static Vector3f check(Vector3f value) {
        Objects.requireNonNull(value, "vector");
        finite(value.x(), "x");
        finite(value.y(), "y");
        finite(value.z(), "z");
        return value;
    }

    /**
     * Copies a validated engine vector into a Jolt vector without axis conversion
     * or normalization. Later engine-vector changes do not change the new value.
     *
     * @param value the finite engine vector
     * @return a new Jolt vector containing X, Y and Z in the same order
     * @throws NullPointerException     if value is null
     * @throws IllegalArgumentException if any component is non-finite
     */
    static Vec3 vector(Vector3f value) {
        check(value);
        return new Vec3(value.x(), value.y(), value.z());
    }

    /**
     * Copies a finite engine position into Jolt's real-valued position type.
     * Units and axes are preserved; float input precision is not increased.
     *
     * @param value the finite world-space position
     * @return a newly constructed Jolt position value
     * @throws NullPointerException     if value is null
     * @throws IllegalArgumentException if any component is non-finite
     */
    static RVec3 position(Vector3f value) {
        check(value);
        return new RVec3(value.x(), value.y(), value.z());
    }

    /**
     * Copies a finite nonzero JOML orientation into a normalized native quaternion.
     * Uses double precision for length evaluation and leaves the source unchanged.
     *
     * @param value nonnull source orientation
     * @return new normalized native quaternion
     * @throws NullPointerException     if value is null
     * @throws IllegalArgumentException if the orientation is zero or nonfinite
     */
    static Quat rotation(Quaternionf value) {
        Objects.requireNonNull(value, "rotation");
        double length = rotationLength(value.x(), value.y(), value.z(), value.w());
        return new Quat((float) (value.x() / length), (float) (value.y() / length),
                (float) (value.z() / length), (float) (value.w() / length));
    }

    /**
     * Validates and normalizes an orientation into caller storage. Source and
     * destination may be the same object.
     *
     * @param value       nonnull source orientation
     * @param destination nonnull output quaternion
     * @return destination
     * @throws NullPointerException     if either quaternion is null
     * @throws IllegalArgumentException if source orientation is zero or nonfinite
     */
    static Quaternionf normalizeRotation(Quaternionf value, Quaternionf destination) {
        Objects.requireNonNull(value, "rotation");
        return normalizeRotation(destination, value.x(), value.y(), value.z(), value.w());
    }

    /**
     * Normalizes explicit quaternion components into caller storage using a
     * double-precision norm.
     *
     * @param destination nonnull output quaternion
     * @param x           X component
     * @param y           Y component
     * @param z           Z component
     * @param w           scalar component
     * @return destination
     * @throws NullPointerException     if destination is null
     * @throws IllegalArgumentException if the components form a zero or nonfinite quaternion
     */
    static Quaternionf normalizeRotation(Quaternionf destination, float x, float y, float z, float w) {
        double length = rotationLength(x, y, z, w);
        return destination.set((float) (x / length), (float) (y / length),
                (float) (z / length), (float) (w / length));
    }

    /**
     * Computes a quaternion's Euclidean norm in double precision and rejects zero
     * or nonfinite results before normalization.
     *
     * @param x X component
     * @param y Y component
     * @param z Z component
     * @param w scalar component
     * @return finite positive norm
     * @throws IllegalArgumentException if the quaternion is zero or nonfinite
     */
    private static double rotationLength(float x, float y, float z, float w) {
        double length = Math.sqrt((double) x * x + (double) y * y + (double) z * z + (double) w * w);
        if (!Double.isFinite(length) || length == 0)
            throw new IllegalArgumentException("Quaternion must be finite and nonzero");
        return length;
    }

    /**
     * Copies a readable Jolt vector into independent engine storage. Components
     * are read as supplied without finiteness checks or normalization.
     *
     * @param value the Jolt vector available for reading
     * @return a newly allocated engine vector
     * @throws NullPointerException if value is null
     */
    static Vector3f vector(Vec3 value) {
        return new Vector3f(value.getX(), value.getY(), value.getZ());
    }

    /**
     * Reads a native real-valued position and narrows each component to float.
     * This can lose precision or overflow for values outside float range; the
     * conversion performs no range or finiteness validation.
     *
     * @param value the readable native position
     * @return a new engine vector in the same world coordinate system
     * @throws NullPointerException if value is null
     */
    static Vector3f position(RVec3 value) {
        return new Vector3f((float) value.xx(), (float) value.yy(), (float) value.zz());
    }

    /**
     * Copies and normalizes a Jolt quaternion into JOML storage.
     * Invalid zero-length or non-finite orientations are rejected rather
     * than replaced with an identity rotation.
     *
     * @param value the readable Jolt orientation
     * @return a newly allocated normalized engine quaternion
     * @throws NullPointerException     if value is null
     * @throws IllegalArgumentException if the supplied quaternion is non-finite or zero-length
     */
    static Quaternionf rotation(Quat value) {
        return normalizeRotation(new Quaternionf(), value.getX(), value.getY(), value.getZ(), value.getW());
    }
}
