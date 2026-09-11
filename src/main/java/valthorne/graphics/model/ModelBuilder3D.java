package valthorne.graphics.model;

import valthorne.graphics.Color;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;

/**
 * Builds CPU triangle models from explicit faces or centered Z-up primitives.
 * Primitive dimensions are finite and positive; outward faces use counterclockwise
 * winding. Triangle constructors copy position and attribute data, and build creates
 * a further model snapshot without clearing this reusable builder.
 *
 * <pre>{@code
 * Model3D floor = ModelBuilder3D.plane(10, 10);
 * Model3D column = ModelBuilder3D.cylinder(0.5f, 3, 24);
 * ModelBuilder3D builder = new ModelBuilder3D();
 * builder.triangle(new Vector3f(), new Vector3f(1, 0, 0),
 *         new Vector3f(0, 1, 0), Color.WHITE);
 * Model3D custom = builder.build();
 * }</pre>
 *
 * <p>No graphics context or GPU allocation is required. The builder is mutable and
 * unsynchronized; completed models provide protected geometry access.</p>
 *
 * @author Albert Beaupre
 */
public final class ModelBuilder3D {
    private final ArrayList<Model3D.Triangle> triangles = new ArrayList<>(); // Accumulated faces retained until explicitly cleared.

    /**
     * Creates a centered XY rectangle at Z zero as two white triangles facing positive Z.
     * UV coordinates cover the unit square; dimensions are full extents.
     *
     * @param width finite positive X extent
     * @param depth finite positive Y extent
     * @return new two-triangle CPU model
     * @throws IllegalArgumentException if either dimension is invalid
     */
    public static Model3D plane(float width, float depth) {
        positive(width);
        positive(depth);
        float x = width / 2, y = depth / 2;
        return new ModelBuilder3D().quad(new Vector3f(-x, -y, 0), new Vector3f(x, -y, 0),
                new Vector3f(x, y, 0), new Vector3f(-x, y, 0), Color.WHITE).build();
    }

    /**
     * Creates a centered box with twelve white triangles and flat outward face normals.
     * Each face receives unit-square UVs independently.
     *
     * @param width finite positive X extent
     * @param depth finite positive Y extent
     * @param height finite positive Z extent
     * @return new closed box model
     * @throws IllegalArgumentException if a dimension is invalid
     */
    public static Model3D box(float width, float depth, float height) {
        positive(width);
        positive(depth);
        positive(height);
        float x = width / 2, y = depth / 2, z = height / 2;
        Vector3f a = new Vector3f(-x, -y, -z), b = new Vector3f(x, -y, -z), c = new Vector3f(x, y, -z), d = new Vector3f(-x, y, -z);
        Vector3f e = new Vector3f(-x, -y, z), f = new Vector3f(x, -y, z), g = new Vector3f(x, y, z), h = new Vector3f(-x, y, z);
        return new ModelBuilder3D().quad(e, f, g, h, Color.WHITE).quad(d, c, b, a, Color.WHITE)
                .quad(a, e, h, d, Color.WHITE).quad(c, g, f, b, Color.WHITE)
                .quad(d, h, g, c, Color.WHITE).quad(a, b, f, e, Color.WHITE).build();
    }

    /**
     * Creates a centered latitude/longitude sphere with radial vertex normals and UVs
     * that span longitude and latitude. Omits degenerate pole triangles, yielding
     * 2 * segments * (stacks - 1) triangles for ordinary counts.
     *
     * @param radius finite positive radius
     * @param segments longitude divisions, at least three
     * @param stacks latitude divisions, at least two
     * @return new sphere model
     * @throws IllegalArgumentException if radius or subdivision counts are invalid
     */
    public static Model3D sphere(float radius, int segments, int stacks) {
        positive(radius);
        if (segments < 3 || stacks < 2)
            throw new IllegalArgumentException("Sphere needs at least 3 segments and 2 stacks");
        ModelBuilder3D builder = new ModelBuilder3D();
        for (int j = 0; j < stacks; j++)
            for (int i = 0; i < segments; i++) {
                float u0 = (float) i / segments, u1 = (float) (i + 1) / segments, v0 = (float) j / stacks, v1 = (float) (j + 1) / stacks;
                Vector3f a = spherePoint(u0, v0), b = spherePoint(u1, v0), c = spherePoint(u1, v1), d = spherePoint(u0, v1);
                if (j > 0) builder.triangle(sphereTriangle(a, b, c, u0, v0, u1, v0, u1, v1, radius));
                if (j < stacks - 1) builder.triangle(sphereTriangle(a, c, d, u0, v0, u1, v1, u0, v1, radius));
            }
        return builder.build();
    }

    /**
     * Creates a centered closed Z-axis cylinder with smooth radial side normals and
     * flat end caps. Side UVs wrap around the circumference; cap UVs map the circular
     * cross-section into a unit square. Each segment contributes four triangles.
     *
     * @param radius finite positive radius
     * @param height finite positive full Z extent
     * @param segments circumference divisions, at least three
     * @return new cylinder model
     * @throws IllegalArgumentException if dimensions or segment count are invalid
     */
    public static Model3D cylinder(float radius, float height, int segments) {
        positive(radius);
        positive(height);
        if (segments < 3) throw new IllegalArgumentException("Cylinder needs at least 3 segments");
        ModelBuilder3D builder = new ModelBuilder3D();
        for (int i = 0; i < segments; i++) {
            double a = i * Math.PI * 2 / segments, b = (i + 1) * Math.PI * 2 / segments;
            Vector3f p = new Vector3f((float) Math.cos(a) * radius, (float) Math.sin(a) * radius, -height / 2);
            Vector3f q = new Vector3f((float) Math.cos(b) * radius, (float) Math.sin(b) * radius, -height / 2);
            Vector3f r = new Vector3f(q).add(0, 0, height), s = new Vector3f(p).add(0, 0, height);
            Vector3f np = new Vector3f(p.x(), p.y(), 0).normalize(), nq = new Vector3f(q.x(), q.y(), 0).normalize();
            float u0 = (float) i / segments, u1 = (float) (i + 1) / segments;
            builder.triangle(new Model3D.Triangle(p, q, r, Color.WHITE, new Vector2f(u0, 0), new Vector2f(u1, 0), new Vector2f(u1, 1), np, nq, nq))
                    .triangle(new Model3D.Triangle(p, r, s, Color.WHITE, new Vector2f(u0, 0), new Vector2f(u1, 1), new Vector2f(u0, 1), np, nq, np))
                    .triangle(new Model3D.Triangle(new Vector3f(0, 0, -height / 2), q, p, Color.WHITE,
                            new Vector2f(.5f, .5f), capUv(q, radius), capUv(p, radius), null, null, null))
                    .triangle(new Model3D.Triangle(new Vector3f(0, 0, height / 2), s, r, Color.WHITE,
                            new Vector2f(.5f, .5f), capUv(s, radius), capUv(r, radius), null, null, null));
        }
        return builder.build();
    }

    /**
     * Converts normalized longitude and latitude parameters to a Z-up unit-sphere point.
     * Values are not clamped; trigonometric functions determine extrapolated coordinates.
     *
     * @param u longitude fraction of a full turn
     * @param v latitude fraction from south to north pole
     * @return newly allocated unit-sphere position
     */
    private static Vector3f spherePoint(float u, float v) {
        double a = u * Math.PI * 2, b = (v - .5) * Math.PI;
        return new Vector3f((float) (Math.cos(a) * Math.cos(b)), (float) (Math.sin(a) * Math.cos(b)), (float) Math.sin(b));
    }

    /**
     * Maps a cap point's XY coordinates from a radius-centered disk into the unit square.
     * Z is ignored and the caller supplies a positive radius.
     *
     * @param p cylinder cap position
     * @param radius cylinder radius
     * @return newly allocated cap texture coordinate
     */
    private static Vector2f capUv(Vector3f p, float radius) {
        return new Vector2f(.5f + p.x() / (2 * radius), .5f + p.y() / (2 * radius));
    }

    /**
     * Scales unit-sphere positions by radius while preserving their radial directions
     * as vertex normals. Assigns white color and the supplied seam-aware UVs.
     *
     * @param a first unit-sphere position
     * @param b second unit-sphere position
     * @param c third unit-sphere position
     * @param ua first U coordinate
     * @param va first V coordinate
     * @param ub second U coordinate
     * @param vb second V coordinate
     * @param uc third U coordinate
     * @param vc third V coordinate
     * @param r sphere radius
     * @return new attributed triangle
     */
    private static Model3D.Triangle sphereTriangle(Vector3f a, Vector3f b, Vector3f c,
                                                   float ua, float va, float ub, float vb, float uc, float vc, float r) {
        return new Model3D.Triangle(new Vector3f(a.x() * r, a.y() * r, a.z() * r),
                new Vector3f(b.x() * r, b.y() * r, b.z() * r), new Vector3f(c.x() * r, c.y() * r, c.z() * r),
                Color.WHITE, new Vector2f(ua, va), new Vector2f(ub, vb), new Vector2f(uc, vc), a, b, c);
    }

    /**
     * Validates a primitive dimension before geometry is allocated.
     *
     * @param value dimension to check
     * @throws IllegalArgumentException if value is non-finite or nonpositive
     */
    private static void positive(float value) {
        if (!Float.isFinite(value) || value <= 0f)
            throw new IllegalArgumentException("Dimension must be finite and positive");
    }

    /**
     * Appends a copied colored triangle with zero UVs and generated flat normals.
     * No degeneracy or winding correction is performed.
     *
     * @param a first position
     * @param b second position
     * @param c third position
     * @param color face color
     * @return this builder
     * @throws NullPointerException if a position or color is null
     */
    public ModelBuilder3D triangle(Vector3f a, Vector3f b, Vector3f c, Color color) {
        triangles.add(new Model3D.Triangle(a, b, c, color));
        return this;
    }

    /**
     * Appends an existing protected triangle object. The completed model copies it
     * during build; callers need not transfer resource ownership.
     *
     * @param triangle attributed face to append
     * @return this builder
     * @throws NullPointerException if triangle is null
     */
    public ModelBuilder3D triangle(Model3D.Triangle triangle) {
        triangles.add(java.util.Objects.requireNonNull(triangle));
        return this;
    }

    /**
     * Appends triangles ABC and ACD with unit-square UVs and generated face normals.
     * Supply ordered coplanar corners for a conventional quad; convexity, planarity,
     * and winding are not validated or corrected.
     *
     * @param a first corner at UV (0,0)
     * @param b second corner at UV (1,0)
     * @param c third corner at UV (1,1)
     * @param d fourth corner at UV (0,1)
     * @param color color copied into both triangles
     * @return this builder
     * @throws NullPointerException if a corner or color is null
     */
    public ModelBuilder3D quad(Vector3f a, Vector3f b, Vector3f c, Vector3f d, Color color) {
        triangles.add(new Model3D.Triangle(a, b, c, color,
                new Vector2f(0, 0), new Vector2f(1, 0), new Vector2f(1, 1), null, null, null));
        triangles.add(new Model3D.Triangle(a, c, d, color,
                new Vector2f(0, 0), new Vector2f(1, 1), new Vector2f(0, 1), null, null, null));
        return this;
    }

    /**
     * Copies accumulated faces into a new CPU model and computes its local bounds.
     * The builder retains its faces for subsequent builds or further additions.
     *
     * @return independent model snapshot, possibly empty
     */
    public Model3D build() {return new Model3D(triangles.toArray(Model3D.Triangle[]::new));}

    /**
     * Removes accumulated faces without affecting models already built. No graphics
     * resources are owned or released by this operation.
     */
    public void clear() {triangles.clear();}
}
