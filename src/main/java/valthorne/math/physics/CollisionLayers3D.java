package valthorne.math.physics;

/**
 * Configures symmetric collision permissions between sixteen user-defined layers.
 * Every pair, including a layer with itself, starts enabled. Changing one pair
 * updates both directions so collision eligibility does not depend on body order.
 *
 * <p>{@link PhysicsWorld3D} reads this matrix when constructing its native filters.
 * Later edits affect this configuration object, not worlds already constructed
 * from it. The world also excludes static/static pairs independently of these
 * permissions; enabling a layer pair does not override that motion-type filter.</p>
 *
 * <pre>{@code
 * CollisionLayers3D layers = new CollisionLayers3D();
 * layers.setCollision(1, 2, false); // Disable both 1-to-2 and 2-to-1 collisions.
 * PhysicsWorld3D world = new PhysicsWorld3D(PhysicsWorld3D.Settings.defaults(), layers);
 * }</pre>
 *
 * <p>This mutable configuration has no synchronization. Finish configuring it
 * before constructing a world that reads it.</p>
 *
 * @author Albert Beaupre
 */
public final class CollisionLayers3D {
    /**
     * Number of user collision layers. Valid indices range from zero through
     * fifteen; native static/moving subdivisions are managed by the world.
     */
    public static final int COUNT = 16;
    private final boolean[][] enabled = new boolean[COUNT][COUNT]; // Symmetric permission table indexed by user layer.

    /**
     * Creates a matrix with all user-layer pairs enabled, including diagonal
     * entries. Configure exceptions with {@link #setCollision(int, int, boolean)}
     * before passing the matrix to a world constructor.
     */
    public CollisionLayers3D() {for (boolean[] row : enabled) java.util.Arrays.fill(row, true);}

    /**
     * Validates a user-layer index for configuration and body settings.
     * Valid indices are returned unchanged for use in assignments.
     *
     * @param layer the candidate user-layer index
     * @return the same validated index
     * @throws IllegalArgumentException if layer is negative or at least {@link #COUNT}
     */
    static int check(int layer) {
        if (layer < 0 || layer >= COUNT) throw new IllegalArgumentException("Collision layer must be in [0,15]");
        return layer;
    }

    /**
     * Enables or disables a pair in both directions. Both indices are checked
     * before mutation; assigning a diagonal entry is supported. Repeating the
     * same setting is harmless and does not update existing native worlds.
     *
     * @param a        the first user-layer index
     * @param b        the second user-layer index
     * @param collides whether the layer pair is permitted to collide
     * @return this configuration for chaining
     * @throws IllegalArgumentException if either layer is outside zero through fifteen
     */
    public CollisionLayers3D setCollision(int a, int b, boolean collides) {
        check(a);
        check(b);
        enabled[a][b] = enabled[b][a] = collides;
        return this;
    }

    /**
     * Reads the configured permission for a validated pair. This reports the
     * user-layer matrix only, without checking body motion types or native filters.
     *
     * @param a the first user-layer index
     * @param b the second user-layer index
     * @return whether this matrix enables the pair
     * @throws IllegalArgumentException if either layer is outside zero through fifteen
     */
    public boolean collides(int a, int b) {
        check(a);
        check(b);
        return enabled[a][b];
    }
}
