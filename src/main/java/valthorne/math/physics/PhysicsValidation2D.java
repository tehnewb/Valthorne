package valthorne.math.physics;

/**
 * Boundary checks shared by planar physics configuration and body operations.
 * Successful checks perform primitive comparisons without allocating objects;
 * descriptive exception messages are constructed only for rejected values.
 */
final class PhysicsValidation2D {
    /**
     * Prevents construction of the validation utility.
     */
    private PhysicsValidation2D() {
    }

    /**
     * Rejects values that cannot be represented by the native simulation.
     *
     * @param value component or scalar to validate
     * @param name parameter name used in failures
     * @throws IllegalArgumentException if the value is infinite or NaN
     */
    static void finite(float value, String name) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    /**
     * Requires a finite, strictly positive scalar.
     *
     * @param value scalar to validate
     * @param name parameter name used in failures
     * @throws IllegalArgumentException if the value is nonpositive or nonfinite
     */
    static void positive(float value, String name) {
        if (!(value > 0) || !Float.isFinite(value))
            throw new IllegalArgumentException(name + " must be finite and positive");
    }

    /**
     * Requires a finite scalar greater than or equal to zero.
     *
     * @param value scalar to validate
     * @param name parameter name used in failures
     * @throws IllegalArgumentException if the value is negative or nonfinite
     */
    static void nonnegative(float value, String name) {
        if (!(value >= 0) || !Float.isFinite(value))
            throw new IllegalArgumentException(name + " must be finite and nonnegative");
    }

    /**
     * Requires a finite coefficient between zero and one, inclusive.
     *
     * @param value coefficient to validate
     * @param name parameter name used in failures
     * @throws IllegalArgumentException if the coefficient lies outside the range
     */
    static void fraction(float value, String name) {
        if (!(value >= 0 && value <= 1))
            throw new IllegalArgumentException(name + " must be between zero and one");
    }
}
