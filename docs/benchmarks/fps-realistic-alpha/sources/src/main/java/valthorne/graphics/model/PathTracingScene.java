package valthorne.graphics.model;

import valthorne.graphics.Color;
import valthorne.graphics.texture.Texture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collects visible triangle-model instances and builds packed geometry, emitter
 * sampling data, and a surface-area-heuristic bounding-volume hierarchy for the GPU
 * path tracer. Visibility means the scene's explicit visibility flags; geometry
 * outside the camera remains available to shadow and secondary rays.
 * <p>
 * Construction captures transforms and computes a signature from model identity
 * and selected material values, but retains model/material references until build.
 * In-place model geometry or texture-pixel edits are not hashed. The renderer must
 * invalidate those changes explicitly. OBJ parts are expanded with combined tint
 * and texture selection, and their pending textures are uploaded during collection,
 * so constructing this snapshot can require the OpenGL context.
 * </p>
 * <p>
 * Call build once on a fresh snapshot, then read its packed arrays. Build appends
 * rather than clearing existing lists. Models, materials, and textures remain
 * borrowed; this package-private object owns CPU lists and packed arrays only.
 * </p>
 * @author Albert Beaupre
 */
final class PathTracingScene {
    final List<Instance> instances = new ArrayList<>(); // Collected model/material references and captured transforms.
    final List<Texture> textures = new ArrayList<>(); // Borrowed distinct textures in atlas-layer order.
    final List<float[]> triangles = new ArrayList<>(); // Packed 48-float triangles, reordered in place during BVH construction.
    final List<float[]> nodes = new ArrayList<>(); // Packed eight-float BVH nodes in depth-first order.
    final List<Integer> emitters = new ArrayList<>(); // Indices of emissive triangles after BVH ordering.
    private long signature = 0xcbf29ce484222325L; // Rolling construction-time signature initialized to the FNV offset basis.
    /**
     * Collects explicitly visible renderables and scene-node hierarchies, capturing
     * each world transform. Direct visible renderables must be model instances;
     * unsupported procedural or other renderables are rejected. Hidden node subtrees
     * are skipped.
     *
     * @param scene source scene
     * @throws IllegalArgumentException if a visible direct renderable is unsupported
     */
    PathTracingScene(Scene3D scene) {
        for (Renderable3D r : scene.getRenderables()) {
            if (!r.isRenderableVisible()) continue;
            if (!(r instanceof ModelInstance3D m))
                throw new IllegalArgumentException("Path tracing currently requires triangle models; unsupported: " + r.getClass().getSimpleName());
            add(m.getModel(), m.getMaterial(), m.getWorldTransform(new Matrix4f()));
        }
        for (SceneNode3D n : scene.getNodes()) visit(n);
    }

    /**
     * Copies three vector components into a packed float array without a fourth
     * padding component.
     *
     * @param target destination array
     * @param offset first destination index
     * @param v vector to copy
     */
    private static void put(float[] target, int offset, Vector3f v) {
        target[offset] = v.x();
        target[offset + 1] = v.y();
        target[offset + 2] = v.z();
    }

    /**
     * Computes a triangle centroid coordinate from its packed base point and two
     * edge vectors: base plus one third of the edge sum.
     *
     * @param t packed triangle
     * @param axis coordinate index from 0 through 2
     * @return centroid coordinate on the selected axis
     */
    private static float centroid(float[] t, int axis) {return t[axis] + (t[4 + axis] + t[8 + axis]) / 3;}

    /**
     * Converts an sRGB component to linear light for emitter luminance weighting.
     * Does not clamp input values.
     *
     * @param x sRGB component
     * @return linear-light component
     */
    private static double linear(float x) {return x <= .04045 ? x / 12.92 : Math.pow((x + .055) / 1.055, 2.4);}

    /**
     * Copies equal-stride records into a new contiguous array. An empty source still
     * produces one zero-filled stride so a nonempty GPU allocation can be created.
     *
     * @param source packed records
     * @param stride floats per record
     * @return independent contiguous data
     */
    private static float[] flatten(List<float[]> source, int stride) {
        float[] result = new float[Math.max(stride, source.size() * stride)];
        for (int i = 0; i < source.size(); i++) System.arraycopy(source.get(i), 0, result, i * stride, stride);
        return result;
    }

    /**
     * Collects a visible scene node and recursively visits its children. A hidden
     * node excludes its entire subtree; a null model still permits visible children.
     *
     * @param n node whose current world transform is captured
     */
    private void visit(SceneNode3D n) {
        if (!n.isVisible()) return;
        add(n.getModel(), n.getMaterial(), n.getWorldTransform(new Matrix4f()));
        for (SceneNode3D c : n.getChildren()) visit(c);
    }

    /**
     * Mixes one integer into the rolling FNV-style signature using XOR and the
     * 64-bit multiplication prime. Overflow is intentional.
     *
     * @param v value to mix
     */
    private void hash(int v) {signature = (signature ^ v) * 0x100000001b3L;}

    /**
     * Mixes the float's canonical bit representation into the scene signature.
     *
     * @param v scalar material or transform component
     */
    private void hash(float v) {hash(Float.floatToIntBits(v));}

    /**
     * Mixes all four color components into the rolling signature in RGBA order.
     *
     * @param c color whose current components are sampled
     */
    private void color(Color c) {
        hash(c.r());
        hash(c.g());
        hash(c.b());
        hash(c.a());
    }

    /**
     * Adds a borrowed model/material pair with its captured transform and hashes
     * the values used to recognize scene changes. Null models are ignored. OBJ
     * models upload pending textures and recursively expand parts, combining tint,
     * translucency, and texture selection with the instance material.
     *
     * @param model source model, possibly null
     * @param material effective instance material
     * @param transform captured model-to-world matrix
     */
    private void add(Model3D model, Material3D material, Matrix4f transform) {
        if (model == null) return;
        if (model instanceof ObjModel3D obj) {
            obj.uploadTextures();
            for (var part : obj.getParts()) {
                Material3D m = material.copy();
                Color a = m.getTint(), b = part.material().getTint();
                m.setTint(new Color(a.r() * b.r(), a.g() * b.g(), a.b() * b.b(), a.a() * b.a()));
                if (m.getRenderPass() == RenderPass3D.OPAQUE && m.getTransmission() <= .01f
                        && (part.material().getRenderPass() == RenderPass3D.TRANSLUCENT || b.a() < 1))
                    m.setRenderPass(RenderPass3D.TRANSLUCENT);
                if (m.getTexture() == null) m.setTexture(part.material().getTexture());
                add(part.model(), m, transform);
            }
            return;
        }
        instances.add(new Instance(model, material, transform));
        hash(System.identityHashCode(model));
        for (int column = 0; column < 4; column++)
            for (int row = 0; row < 4; row++) hash(transform.get(column, row));
        color(material.getTint());
        color(material.getEmissive());
        hash(material.getRoughness());
        hash(material.getMetallic());
        hash(material.getTransmission());
        hash(material.getIndexOfRefraction());
        hash(material.getEmissionStrength());
        hash(material.isEmissionLightEnabled() ? 1 : 0);
        hash(material.getRenderPass().ordinal());
        hash(material.isCastsShadow() ? 1 : 0);
        hash(material.isReceivesShadow() ? 1 : 0);
        hash(material.getAlphaCutoff());
        hash(System.identityHashCode(material.getTexture()));
    }

    /**
     * Returns the construction-time signature of collected identities, transforms,
     * and material values. It is a change-detection hash rather than a collision-free
     * identity or a live hash of subsequently mutated source data.
     *
     * @return rolling scene signature
     */
    long signature() {return signature;}

    /**
     * Appends world-space triangles for collected instances, skipping areas at most
     * 1e-10, and transforms normals with the inverse transpose. Assigns texture
     * layers and per-instance surface IDs, then reorders triangles while building
     * the BVH. Finally records emissive triangle indices and area/luminance-weighted
     * sampling probabilities.
     * <p>
     * Each triangle occupies twelve vec4s: base/area, two edges, three normals,
     * tinted color, emission, BSDF parameters, UVs, and texture/cutoff/probability/
     * surface metadata. Emission entries are selected from positive packed emission;
     * the material emission-light flag is hashed but is not used as a filter here.
     * Call once per fresh snapshot because existing output lists are not cleared.
     * </p>
     *
     * @throws IllegalStateException if an instance's linear transform is effectively singular
     */
    void build() {
        int surfaceId = 0;
        for (Instance i : instances) {
            Material3D m = i.material;
            Texture texture = m.getTexture();
            int layer = -1;
            if (texture != null) {
                layer = textures.indexOf(texture);
                if (layer < 0) {
                    layer = textures.size();
                    textures.add(texture);
                }
            }
            if (Math.abs(i.transform.determinant3x3()) < 1e-20f)
                throw new IllegalStateException("Singular normal transform");
            org.joml.Matrix3f normalTransform = i.transform.normal(new org.joml.Matrix3f());
            for (Model3D.Triangle t : i.model.triangles()) {
                Vector3f a = i.transform.transformPosition(t.a, new Vector3f()), b = i.transform.transformPosition(t.b, new Vector3f()), c = i.transform.transformPosition(t.c, new Vector3f());
                Vector3f e1 = new Vector3f(b).sub(a), e2 = new Vector3f(c).sub(a);
                float area = new Vector3f(e1).cross(e2).length() * .5f;
                if (!(area > 1e-10f)) continue;
                // 12 vec4s: geometry, interpolated normals, color, emission, BSDF, UVs, texture/cutoff.
                float[] v = new float[48];
                put(v, 0, a);
                put(v, 4, e1);
                put(v, 8, e2);
                v[3] = area;
                Vector3f normal = normalTransform.transform(t.normalA, new Vector3f());
                if (normal.lengthSquared() != 0f) normal.normalize();
                put(v, 12, normal);
                normalTransform.transform(t.normalB, normal);
                if (normal.lengthSquared() != 0f) normal.normalize();
                put(v, 16, normal);
                normalTransform.transform(t.normalC, normal);
                if (normal.lengthSquared() != 0f) normal.normalize();
                put(v, 20, normal);
                Color tint = m.getTint(), tc = t.color, em = m.getEmissive();
                v[24] = tint.r() * tc.r();
                v[25] = tint.g() * tc.g();
                v[26] = tint.b() * tc.b();
                v[27] = tint.a() * tc.a();
                v[28] = em.r();
                v[29] = em.g();
                v[30] = em.b();
                v[31] = em.a() * m.getEmissionStrength();
                v[32] = m.getRoughness();
                v[33] = m.getMetallic();
                v[34] = m.getTransmission();
                v[35] = m.getIndexOfRefraction();
                v[36] = t.uvA.x();
                v[37] = t.uvA.y();
                v[38] = t.uvB.x();
                v[39] = t.uvB.y();
                v[40] = t.uvC.x();
                v[41] = t.uvC.y();
                v[42] = layer;
                v[43] = m.getAlphaCutoff();
                v[45] = surfaceId;
                triangles.add(v);
            }
            surfaceId++;
        }
        if (!triangles.isEmpty()) buildNode(0, triangles.size(), 0);
        for (int i = 0; i < triangles.size(); i++) {
            float[] t = triangles.get(i);
            if (t[31] > 0 && (t[28] + t[29] + t[30]) > 0) emitters.add(i);
        }
        double total = 0;
        for (int index : emitters) {
            float[] t = triangles.get(index);
            total += t[3] * (double) t[31] * (.2126 * linear(t[28]) + .7152 * linear(t[29]) + .0722 * linear(t[30]));
        }
        for (int index : emitters) {
            float[] t = triangles.get(index);
            t[44] = (float) (t[3] * (double) t[31] * (.2126 * linear(t[28]) + .7152 * linear(t[29]) + .0722 * linear(t[30])) / total);
        }
    }

    /**
     * Builds a depth-first packed BVH over a half-open triangle range. Leaves hold
     * up to four triangles unless the depth cap of 28 is reached. Internal splits
     * minimize surface-area times primitive-count cost across sorted centroid axes.
     * Sorting mutates the triangle list.
     * <p>
     * Each node has eight floats: minimum XYZ and a link, then maximum XYZ and a
     * count/tag. A leaf stores the starting triangle and positive count. An internal
     * node stores the right-child index and negative (axis + 1); its left child
     * immediately follows it in depth-first storage.
     * </p>
     *
     * @param start inclusive triangle index
     * @param end exclusive triangle index
     * @param depth current recursion depth
     * @return newly appended node index
     */
    private int buildNode(int start, int end, int depth) {
        int index = nodes.size();
        float[] n = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, 0, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, 0};
        nodes.add(n);
        for (int j = start; j < end; j++) {
            float[] t = triangles.get(j);
            for (int a = 0; a < 3; a++) {
                float p = t[a], q = p + t[4 + a], r = p + t[8 + a];
                n[a] = Math.min(n[a], Math.min(p, Math.min(q, r)));
                n[4 + a] = Math.max(n[4 + a], Math.max(p, Math.max(q, r)));
            }
        }
        if (end - start <= 4 || depth >= 28) {
            n[3] = start;
            n[7] = end - start;
            return index;
        }
        int axis = 0;
        if (n[5] - n[1] > n[4] - n[0]) axis = 1;
        if (n[6] - n[2] > n[4 + axis] - n[axis]) axis = 2;
        // Surface-area splits isolate large room planes from dense curved meshes.
        // The depth cap bounds the GPU traversal stack even for uneven partitions.
        int bestLeft = (end - start) / 2;
        double bestCost = Double.POSITIVE_INFINITY;
        int count = end - start;
        float[] suffix = new float[count];
        for (int candidate = 0; candidate < 3; candidate++) {
            final int a = candidate;
            triangles.subList(start, end).sort(Comparator.comparingDouble(t -> centroid(t, a)));
            float[] bounds = emptyBounds();
            for (int j = count - 1; j >= 0; j--) {
                extend(bounds, triangles.get(start + j));
                suffix[j] = area(bounds) * (count - j);
            }
            bounds = emptyBounds();
            for (int j = 1; j < count; j++) {
                extend(bounds, triangles.get(start + j - 1));
                double cost = area(bounds) * j + suffix[j];
                if (cost < bestCost) {bestCost = cost; axis = candidate; bestLeft = j;}
            }
        }
        n[7] = -(axis + 1); // Internal nodes encode the split axis for direction-ordered traversal.
        final int sortAxis = axis;
        triangles.subList(start, end).sort(Comparator.comparingDouble(t -> centroid(t, sortAxis)));
        int mid = start + bestLeft;
        buildNode(start, mid, depth + 1);
        n[3] = buildNode(mid, end, depth + 1);
        return index;
    }

    /**
     * Creates a six-float min/max accumulator with positive-infinite minima and
     * negative-infinite maxima, ready for its first triangle.
     *
     * @return new empty bounds storage
     */
    private static float[] emptyBounds() {
        return new float[]{Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
    }

    /**
     * Expands a six-float bounds accumulator to include the three vertices reconstructed
     * from a packed triangle's base and edges.
     *
     * @param bounds mutable minimum XYZ followed by maximum XYZ
     * @param triangle packed triangle record
     */
    private static void extend(float[] bounds, float[] triangle) {
        for (int a = 0; a < 3; a++) {
            float p = triangle[a], q = p + triangle[4 + a], r = p + triangle[8 + a];
            bounds[a] = Math.min(bounds[a], Math.min(p, Math.min(q, r)));
            bounds[3 + a] = Math.max(bounds[3 + a], Math.max(p, Math.max(q, r)));
        }
    }

    /**
     * Computes box surface area for BVH split cost without validating the bounds.
     *
     * @param bounds valid minimum/maximum box
     * @return twice the sum of pairwise extent products
     */
    private static float area(float[] bounds) {
        float x = bounds[3] - bounds[0], y = bounds[4] - bounds[1], z = bounds[5] - bounds[2];
        return 2 * (x * y + y * z + z * x);
    }

    /**
     * Flattens the current BVH-ordered triangle records into independent upload data.
     * Must follow build for a complete snapshot.
     *
     * @return packed 48-float records, or one zero record when empty
     */
    float[] triangleData() {return flatten(triangles, 48);}

    /**
     * Builds pairs of triangle index and cumulative emitter probability from the
     * BVH-ordered records. Forces the final cumulative value to one to avoid rounding
     * gaps. Empty scenes return one zero pair.
     *
     * @return independent two-float emitter records
     */
    float[] emitterData() {
        float[] result = new float[Math.max(2, emitters.size() * 2)];
        double sum = 0;
        for (int i = 0; i < emitters.size(); i++) {
            int index = emitters.get(i);
            sum += triangles.get(index)[44];
            result[i * 2] = index;
            result[i * 2 + 1] = (float) sum;
        }
        if (!emitters.isEmpty()) result[result.length - 1] = 1;
        return result;
    }

    /**
     * Flattens depth-first BVH nodes into independent upload data.
     *
     * @return packed eight-float nodes, or one zero node when empty
     */
    float[] nodeData() {return flatten(nodes, 8);}

    /**
     * Collected geometry source and effective material with a captured world matrix.
     * The record itself does not defensively copy its mutable components.
     *
     * @param model borrowed source triangle model
     * @param material borrowed effective material
     * @param transform captured world transform retained by reference
     * @author Albert Beaupre
     */
    record Instance(Model3D model, Material3D material, Matrix4f transform) {
    }
}
