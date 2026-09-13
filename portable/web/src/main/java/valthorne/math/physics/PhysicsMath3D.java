package valthorne.math.physics;

import org.joml.Quaternionf;
import org.joml.Vector3f;

final class PhysicsMath3D {

    private PhysicsMath3D() {}

    static float finite(float value, String name) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return value;
    }

    static float positive(float value, String name) {
        if (finite(value, name) <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    static float nonnegative(float value, String name) {
        if (finite(value, name) < 0) throw new IllegalArgumentException(name + " must be nonnegative");
        return value;
    }

    static Vector3f check(Vector3f value) {
        java.util.Objects.requireNonNull(value, "vector");
        finite(value.x(), "x");
        finite(value.y(), "y");
        finite(value.z(), "z");
        return value;
    }

    static Quaternionf normalizeRotation(Quaternionf value, Quaternionf destination) {
        java.util.Objects.requireNonNull(value, "rotation");
        return normalizeRotation(destination, value.x(), value.y(), value.z(), value.w());
    }

    static Quaternionf normalizeRotation(Quaternionf destination, float x, float y, float z, float w) {
        double length = rotationLength(x, y, z, w);
        return destination.set((float) (x / length), (float) (y / length),
                (float) (z / length), (float) (w / length));
    }

    private static double rotationLength(float x, float y, float z, float w) {
        double length = Math.sqrt((double) x * x + (double) y * y + (double) z * z + (double) w * w);
        if (!Double.isFinite(length) || length == 0)
            throw new IllegalArgumentException("Quaternion must be finite and nonzero");
        return length;
    }

}
