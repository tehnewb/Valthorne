package valthorne.graphics.model;

import valthorne.graphics.Color;
import org.joml.primitives.AABBf;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * CPU-side triangle geometry conventionally expressed in Z-up local space. The
 * constructor copies triangles and calculates an axis-aligned local bound; no GPU
 * resources are allocated. Public accessors protect positions, attributes, and bounds
 * with copies, while package-internal rendering code reads trusted shared storage.
 *
 * <p>The model imposes no automatic axis conversion or unit scale and does not reject
 * degenerate or non-finite geometry. An empty triangle array is accepted. Materials,
 * world transforms, and instance visibility belong to separate rendering objects.</p>
 *
 * @author Albert Beaupre
 */
public class Model3D {

    private final Triangle[] triangles; // Owned triangle snapshots, shared only with trusted package code.
    private final AABBf localBounds = new AABBf(); // Local bounds accumulated during construction.

    /**
     * Copies each triangle and accumulates bounds from its three positions. The source
     * array and triangle objects are not retained, and an empty model is allowed.
     *
     * @param triangles ordered triangle data to snapshot
     * @throws NullPointerException if the array or any triangle is null
     */
    public Model3D(Triangle[] triangles) {
        if (triangles == null) throw new NullPointerException("triangles");
        this.triangles = new Triangle[triangles.length];
        for (int i = 0; i < triangles.length; i++) {
            Triangle triangle = triangles[i];
            if (triangle == null) {
                throw new NullPointerException("triangles[" + i + "]");
            }
            this.triangles[i] = triangle.copy();
            localBounds.union(triangle.getA());
            localBounds.union(triangle.getB());
            localBounds.union(triangle.getC());
        }
    }

    /**
     * Returns a new array containing the model's protected triangle objects. Changing
     * array membership does not affect the model; triangle public accessors return copies.
     *
     * @return shallow array copy of triangle snapshots
     */
    public Triangle[] getTriangles() {
        return triangles.clone();
    }

    /**
     * Exposes internal triangle storage to trusted package rendering code without an
     * array allocation. Callers must not replace elements or mutate triangle attributes.
     *
     * @return owned triangle array
     */
    Triangle[] triangles() {return triangles;}

    // Trusted package access; callers must not modify the stored geometry bound.
    /**
     * Borrows the construction-time model-local geometry bound without allocating a copy.
     * Trusted rendering code must not mutate it; public callers should use the copying accessor.
     * @return owned mutable bound exposed read-only by convention
     */
    AABBf localBounds() {return localBounds;}

    /**
     * Returns geometry size without copying or traversing the triangle array.
     *
     * @return number of stored triangles
     */
    public int getTriangleCount() {
        return triangles.length;
    }

    /**
     * Returns an independent copy of bounds computed at construction. It remains in
     * model-local coordinates and includes no instance transform.
     *
     * @return copied local axis-aligned bounds
     */
    public AABBf getLocalBounds() {
        return new AABBf(localBounds);
    }

    /**
     * Stores three copied positions, one copied color, UV coordinates, and vertex normals.
     * Missing individual normals use the normalized cross product of the two face edges;
     * supplied normals are copied and normalized independently. Degenerate faces can
     * retain zero normals. Public getters allocate copies; package readers must not mutate.
     *
     * @author Albert Beaupre
     */
    public static final class Triangle {
        // Package access lets the renderer read immutable geometry without allocating copies per vertex.
        final Vector3f a; // Copied first local-space position.
        final Vector3f b; // Copied second local-space position.
        final Vector3f c; // Copied third local-space position.
        final Color color; // Copied per-triangle color.
        final Vector2f uvA, uvB, uvC; // Copied UV coordinates in vertex order.
        final Vector3f normalA, normalB, normalC; // Copied normalized vertex normals or flat-face fallbacks.

        /**
         * Copies a colored face with zero UVs and a generated flat normal at each vertex.
         * The cross-product winding determines the normal direction.
         *
         * @param a first local position
         * @param b second local position
         * @param c third local position
         * @param color triangle color
         * @throws NullPointerException if a position or color is null
         */
        public Triangle(Vector3f a, Vector3f b, Vector3f c, Color color) {
            this(a, b, c, color, new Vector2f(), new Vector2f(), new Vector2f(), null, null, null);
        }

        /**
         * Copies all supplied attributes. Each null normal independently uses the face normal;
         * non-null normals are normalized without finiteness or degeneracy checks. UVs must
         * be non-null and are copied without clamping.
         *
         * @param a first local position
         * @param b second local position
         * @param c third local position
         * @param color triangle color
         * @param uvA first texture coordinate
         * @param uvB second texture coordinate
         * @param uvC third texture coordinate
         * @param normalA first normal, or null for flat shading
         * @param normalB second normal, or null for flat shading
         * @param normalC third normal, or null for flat shading
         * @throws NullPointerException if a position, color, or UV is null
         */
        public Triangle(Vector3f a, Vector3f b, Vector3f c, Color color,
                        Vector2f uvA, Vector2f uvB, Vector2f uvC,
                        Vector3f normalA, Vector3f normalB, Vector3f normalC) {
            if (a == null) throw new NullPointerException("a");
            if (b == null) throw new NullPointerException("b");
            if (c == null) throw new NullPointerException("c");
            if (color == null) throw new NullPointerException("color");
            this.a = new Vector3f(a);
            this.b = new Vector3f(b);
            this.c = new Vector3f(c);
            this.color = color.copy();
            this.uvA = new Vector2f(uvA.x(), uvA.y());
            this.uvB = new Vector2f(uvB.x(), uvB.y());
            this.uvC = new Vector2f(uvC.x(), uvC.y());
            Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
            if (normal.lengthSquared() != 0f) normal.normalize();
            this.normalA = new Vector3f(normalA == null ? normal : normalA);
            if (this.normalA.lengthSquared() != 0f) this.normalA.normalize();
            this.normalB = new Vector3f(normalB == null ? normal : normalB);
            if (this.normalB.lengthSquared() != 0f) this.normalB.normalize();
            this.normalC = new Vector3f(normalC == null ? normal : normalC);
            if (this.normalC.lengthSquared() != 0f) this.normalC.normalize();
        }

        /**
         * Returns an independent copy of vertex A's texture coordinates. Values are
         * stored as supplied and need not lie within zero through one.
         *
         * @return copied UV coordinate
         */
        public Vector2f getUvA() {return new Vector2f(uvA.x(), uvA.y());}

        /**
         * Returns an independent copy of vertex B's texture coordinates. Values are
         * stored as supplied and need not lie within zero through one.
         *
         * @return copied UV coordinate
         */
        public Vector2f getUvB() {return new Vector2f(uvB.x(), uvB.y());}

        /**
         * Returns an independent copy of vertex C's texture coordinates. Values are
         * stored as supplied and need not lie within zero through one.
         *
         * @return copied UV coordinate
         */
        public Vector2f getUvC() {return new Vector2f(uvC.x(), uvC.y());}

        /**
         * Returns an independent copy of vertex A's stored local-space normal.
         * Degenerate or non-finite source data is not repaired by this accessor.
         *
         * @return copied normal
         */
        public Vector3f getNormalA() {return new Vector3f(normalA);}

        /**
         * Returns an independent copy of vertex B's stored local-space normal.
         * Degenerate or non-finite source data is not repaired by this accessor.
         *
         * @return copied normal
         */
        public Vector3f getNormalB() {return new Vector3f(normalB);}

        /**
         * Returns an independent copy of vertex C's stored local-space normal.
         * Degenerate or non-finite source data is not repaired by this accessor.
         *
         * @return copied normal
         */
        public Vector3f getNormalC() {return new Vector3f(normalC);}

        /**
         * Returns an independent copy of vertex A's local-space position.
         *
         * @return copied position
         */
        public Vector3f getA() {
            return new Vector3f(a);
        }

        /**
         * Returns an independent copy of vertex B's local-space position.
         *
         * @return copied position
         */
        public Vector3f getB() {
            return new Vector3f(b);
        }

        /**
         * Returns an independent copy of vertex C's local-space position.
         *
         * @return copied position
         */
        public Vector3f getC() {
            return new Vector3f(c);
        }

        /**
         * Copies the triangle's color so callers can modify it without altering geometry.
         *
         * @return independent color value
         */
        public Color getColor() {
            return color.copy();
        }

        /**
         * Creates a full attribute copy through the validating constructor. Normals undergo
         * normalization again, so floating-point rounding may differ slightly.
         *
         * @return independent triangle snapshot
         */
        private Triangle copy() {
            return new Triangle(a, b, c, color, uvA, uvB, uvC, normalA, normalB, normalC);
        }
    }
}
