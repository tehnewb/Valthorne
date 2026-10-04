package valthorne.math.physics;

import java.util.Objects;
import valthorne.math.geometry.Shape;

/**
 * Reusable fluent configuration for a planar rigid body. A world copies these
 * values when creating a body; later changes affect only future bodies. Settings
 * own no native resources and should be configured on the calling thread.
 *
 * <p>Defaults describe a one-kilogram dynamic body at the origin with zero
 * velocity, friction 0.2, zero restitution, damping 0.05, normal gravity, and
 * sleeping enabled. Positions and shape dimensions are meters, velocities are
 * meters per second, and rotations are counterclockwise radians around Z.</p>
 *
 * <pre>{@code
 * BodySettings2D settings = new BodySettings2D(CollisionShape2D.box(0.6f, 1.8f))
 *         .position(2, 4)
 *         .mass(75)
 *         .fixedRotation(true)
 *         .continuous(true);
 * RigidBody2D player = world.createBody(settings);
 * }</pre>
 */
public final class BodySettings2D {
    final PhysicsGeometry2D geometry; // Optional geometry snapshot, owning no native resources.
    final CollisionShape2D shape; // Immutable collision geometry retained for future body creation.
    MotionType2D motion = MotionType2D.DYNAMIC; // Simulation response of newly created bodies.
    boolean positionInPixels; // Whether the initial center awaits conversion using the creating world.
    float x; // Initial horizontal center in meters.
    float y; // Initial vertical center in meters.
    float angle; // Initial counterclockwise orientation in radians.
    float velocityX; // Initial horizontal velocity in meters per second.
    float velocityY; // Initial vertical velocity in meters per second.
    float angularVelocity; // Initial counterclockwise angular velocity in radians per second.
    float mass = 1; // Dynamic body mass in kilograms; ignored for other motion types.
    float friction = 0.2f; // Nonnegative coefficient used by contact friction.
    float restitution; // Fraction of normal impact velocity retained after collision.
    float linearDamping = 0.05f; // Native linear damping coefficient between zero and one.
    float angularDamping = 0.05f; // Native angular damping coefficient between zero and one.
    float gravityFactor = 1; // Multiplier of the world's gravity, including negative values.
    boolean fixedRotation; // Whether angular motion around Z is disabled.
    boolean sleeping = true; // Whether inactive dynamic bodies may sleep.
    boolean continuous; // Whether native linear-cast collision detection is enabled.
    boolean sensor; // Whether contacts detect overlap without applying collision response.

    /**
     * Creates default dynamic-body settings for the supplied geometry.
     *
     * @param shape reusable collision descriptor, not null
     * @throws NullPointerException if the shape is null
     */
    public BodySettings2D(CollisionShape2D shape) {
        this.shape = Objects.requireNonNull(shape, "shape");
        geometry = null;
    }

    /**
     * Creates physics from a geometry shape expressed in pixels and retains that
     * exact object for automatic rendering updates. Position, outline and degree
     * rotation are copied now. Each shape may belong to only one live body per
     * world; the caller must also avoid sharing it across worlds or threads.
     * Shapes must retain their dimensions and vertex count while attached.
     *
     * <pre>{@code
     * Rectangle rectangle = new Rectangle(100, 200, 64, 32);
     * RigidBody2D body = world.createBody(world.bodySettings(rectangle).mass(2));
     * world.update(deltaSeconds); // rectangle now contains the interpolated render pose
     * }</pre>
     * @param shape circle, rectangle, rounded rectangle, triangle or convex polygon
     * @param world owning world supplying pixel configuration, not null
     */
    BodySettings2D(Shape shape, PhysicsWorld2D world) {
        this(new PhysicsGeometry2D(shape, Objects.requireNonNull(world, "world")));
    }

    /**
     * Creates geometry-backed settings from coordinates already expressed in meters.
     * Geometry degree rotations are converted automatically to world radians.
     * @param shape supported geometry shape, not null
     * @return new reusable settings retaining the original shape
     */
    public static BodySettings2D fromGeometry(Shape shape) {
        /*
         * A named factory avoids ambiguity with the existing collision-descriptor constructor.
         */
        return new BodySettings2D(new PhysicsGeometry2D(shape, null));
    }

    /**
     * Initializes body configuration from a validated geometry snapshot.
     * @param geometry immutable outline and original render object
     */
    private BodySettings2D(PhysicsGeometry2D geometry) {
        shape = geometry.collision();
        this.geometry = geometry;
        if (geometry.pixelWorld == null) position(geometry.centerX, geometry.centerY);
        else position(geometry.pixelWorld.worldX(geometry.centerX), geometry.pixelWorld.worldY(geometry.centerY));
        rotation(geometry.pixelWorld == null ? (float) Math.toRadians(geometry.shape.getRotation()) : geometry.pixelWorld.toWorldRotation(geometry.shape.getRotation()));
    }

    /**
     * Selects the initial motion type and native mass capabilities.
     *
     * @param motion desired motion type, not null
     * @return these settings
     */
    public BodySettings2D motion(MotionType2D motion) {
        this.motion = Objects.requireNonNull(motion, "motion");
        return this;
    }

    /**
     * Sets the initial center in world meters.
     *
     * @param x horizontal center
     * @param y vertical center
     * @return these settings
     * @throws IllegalArgumentException if a component is nonfinite
     */
    public BodySettings2D position(float x, float y) {
        PhysicsValidation2D.finite(x, "x");
        PhysicsValidation2D.finite(y, "y");
        positionInPixels = false;
        this.x = x;
        this.y = y;
        return this;
    }

    /**
     * Sets an initial pixel center, converted using the creating world's configuration.
     * @param x finite horizontal pixel center
     * @param y finite vertical pixel center
     * @return these settings
     */
    public BodySettings2D positionPixels(float x, float y) {
        position(x, y);
        positionInPixels = true;
        return this;
    }

    /**
     * Sets the initial counterclockwise rotation around Z.
     *
     * @param radians finite orientation in radians
     * @return these settings
     */
    public BodySettings2D rotation(float radians) {
        PhysicsValidation2D.finite(radians, "rotation");
        angle = radians;
        return this;
    }

    /**
     * Sets initial linear velocity; static bodies ignore this value.
     *
     * @param x horizontal meters per second
     * @param y vertical meters per second
     * @return these settings
     */
    public BodySettings2D velocity(float x, float y) {
        PhysicsValidation2D.finite(x, "velocity x");
        PhysicsValidation2D.finite(y, "velocity y");
        velocityX = x;
        velocityY = y;
        return this;
    }

    /**
     * Sets initial angular velocity; static bodies and fixed rotation ignore it.
     *
     * @param radiansPerSecond finite counterclockwise radians per second
     * @return these settings
     */
    public BodySettings2D angularVelocity(float radiansPerSecond) {
        PhysicsValidation2D.finite(radiansPerSecond, "angular velocity");
        angularVelocity = radiansPerSecond;
        return this;
    }

    /**
     * Sets dynamic mass. Jolt scales the shape's inertia to this mass.
     *
     * @param kilograms finite mass greater than zero
     * @return these settings
     */
    public BodySettings2D mass(float kilograms) {
        PhysicsValidation2D.positive(kilograms, "mass");
        mass = kilograms;
        return this;
    }

    /**
     * Sets the nonnegative contact friction coefficient.
     *
     * @param coefficient finite friction coefficient
     * @return these settings
     */
    public BodySettings2D friction(float coefficient) {
        PhysicsValidation2D.nonnegative(coefficient, "friction");
        friction = coefficient;
        return this;
    }

    /**
     * Sets the retained fraction of normal impact velocity.
     *
     * @param coefficient restitution between zero and one
     * @return these settings
     */
    public BodySettings2D restitution(float coefficient) {
        PhysicsValidation2D.fraction(coefficient, "restitution");
        restitution = coefficient;
        return this;
    }

    /**
     * Sets native damping coefficients for linear and angular motion.
     *
     * @param linear linear coefficient between zero and one
     * @param angular angular coefficient between zero and one
     * @return these settings
     */
    public BodySettings2D damping(float linear, float angular) {
        PhysicsValidation2D.fraction(linear, "linear damping");
        PhysicsValidation2D.fraction(angular, "angular damping");
        linearDamping = linear;
        angularDamping = angular;
        return this;
    }

    /**
     * Sets the body's gravity multiplier; zero disables gravity and negative
     * values reverse it. This affects dynamic bodies only.
     *
     * @param factor finite gravity multiplier
     * @return these settings
     */
    public BodySettings2D gravityFactor(float factor) {
        PhysicsValidation2D.finite(factor, "gravity factor");
        gravityFactor = factor;
        return this;
    }

    /**
     * Disables or enables angular simulation while preserving initial orientation.
     *
     * @param fixed whether to lock rotation
     * @return these settings
     */
    public BodySettings2D fixedRotation(boolean fixed) {
        fixedRotation = fixed;
        return this;
    }

    /**
     * Controls whether inactive dynamic bodies can stop consuming solver work.
     *
     * @param enabled whether sleeping is permitted
     * @return these settings
     */
    public BodySettings2D sleeping(boolean enabled) {
        sleeping = enabled;
        return this;
    }

    /**
     * Enables native linear-cast collision detection for fast dynamic bodies.
     * Sensors use discrete overlap detection regardless of this setting.
     *
     * @param enabled whether to request continuous collision detection
     * @return these settings
     */
    public BodySettings2D continuous(boolean enabled) {
        continuous = enabled;
        return this;
    }

    /**
     * Controls whether the body detects overlap without collision response.
     * Static sensors detect dynamic bodies; kinematic sensors also detect statics.
     *
     * @param enabled whether the body is a sensor
     * @return these settings
     */
    public BodySettings2D sensor(boolean enabled) {
        sensor = enabled;
        return this;
    }
}
