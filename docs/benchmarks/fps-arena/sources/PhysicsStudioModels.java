package valthorne.examples;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.primitives.AABBf;

import valthorne.graphics.model.Material3D;
import valthorne.graphics.model.Model3D;
import valthorne.graphics.model.ObjModel3D;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reusable CC0 model gallery for the physics studio. Geometry and collision queries use the same
 * baked Z-up coordinates: centered in XY, with the base at Z=0. Loading is CPU-only; close on the
 * render thread if model textures have been uploaded. Poly Haven props retain their 2K albedo
 * textures; the additional Kenney furniture retains authored material colors.
 */
public final class PhysicsStudioModels implements AutoCloseable {
    private static final String ROOT = "valthorne/physics-studio/";
    private final List<Entry> entries;
    private boolean closed;

    /** Model dimensions are full extents in the normalized model's own coordinates. */
    public record Entry(
            String name,
            ObjModel3D model,
            float width,
            float depth,
            float height,
            float roughness,
            float yawDegrees) {
        public Material3D material() {
            return new Material3D().setRoughness(roughness);
        }

        public Quaternionf rotation() {
            return new Quaternionf().rotationZ((float) Math.toRadians(yawDegrees));
        }
    }

    public PhysicsStudioModels() {
        List<Entry> loaded = new ArrayList<>(6);
        try {
            loaded.add(
                    load(
                            "Antique ceramic vase",
                            "realistic/antique_ceramic_vase_01",
                            2.2f,
                            .3f,
                            1));
            loaded.add(
                    load("Weathered military crate", "realistic/old_military_crate", 1.6f, .8f, 1));
            loaded.add(load("Marble bust", "realistic/marble_bust_01", 2.4f, .55f, 1, 180));
            loaded.add(load("Design sofa", "loungeDesignSofa", 1.6f));
            loaded.add(load("Lounge chair", "loungeChairRelax", 1.8f));
            loaded.add(load("Desk chair", "chairDesk", 2.0f));
            entries = List.copyOf(loaded);
        } catch (RuntimeException | Error failure) {
            for (Entry entry : loaded) entry.model().dispose();
            throw failure;
        }
    }

    /** Entries and their geometry can be shared by any number of scene instances. */
    public List<Entry> entries() {
        if (closed) throw new IllegalStateException("Physics studio models are closed");
        return entries;
    }

    private static Entry load(String name, String file, float height) {
        return load(name, "models/" + file, height, .4f, 2);
    }

    private static Entry load(
            String name, String file, float height, float roughness, int minimumParts) {
        return load(name, file, height, roughness, minimumParts, 0);
    }

    private static Entry load(
            String name,
            String file,
            float height,
            float roughness,
            int minimumParts,
            float yawDegrees) {
        String source = ROOT + file + ".obj";
        byte[] scaled;
        try {
            scaled = scaleVertices(readResource(source), height);
        } catch (IOException failure) {
            throw new UncheckedIOException("Failed to load gallery model " + source, failure);
        }
        // The OBJ loader converts Y-up to Z-up, reverses winding to match the axis
        // swap, centers XY, grounds the base, and preserves all material groups.
        ObjModel3D model =
                ObjModel3D.load(
                        source, path -> path.equals(source) ? scaled : readResource(path), true);
        try {
            validate(model, height, source, minimumParts);
            AABBf bounds = model.getLocalBounds();
            return new Entry(
                    name,
                    model,
                    bounds.maxX - bounds.minX,
                    bounds.maxY - bounds.minY,
                    bounds.maxZ - bounds.minZ,
                    roughness,
                    yawDegrees);
        } catch (RuntimeException | Error failure) {
            model.dispose();
            throw failure;
        }
    }

    private static byte[] readResource(String path) throws IOException {
        String resource = path.replace('\\', '/');
        if (!resource.startsWith(ROOT) || resource.contains("../"))
            throw new IOException("Model dependency is outside the gallery: " + resource);
        try (InputStream input = PhysicsStudioModels.class.getResourceAsStream("/" + resource)) {
            if (input == null) throw new IOException("Missing gallery resource: " + resource);
            return input.readAllBytes();
        }
    }

    /** Bake uniform size into vertices while preserving UVs, normals, groups and MTL references. */
    private static byte[] scaleVertices(byte[] data, float targetHeight) {
        List<String> lines = new String(data, StandardCharsets.UTF_8).lines().toList();
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (String line : lines) {
            String[] fields = vertexFields(line);
            if (fields == null) continue;
            float y = coordinate(fields[2]);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        float sourceHeight = maxY - minY;
        if (!(sourceHeight > 0) || !Float.isFinite(sourceHeight))
            throw new IllegalArgumentException("Gallery OBJ must have a finite positive Y extent");
        float scale = targetHeight / sourceHeight;
        StringBuilder result = new StringBuilder(data.length + 1024);
        for (String line : lines) {
            String[] fields = vertexFields(line);
            if (fields == null) {
                result.append(line);
            } else {
                result.append('v');
                for (int axis = 1; axis <= 3; axis++)
                    result.append(' ').append(coordinate(fields[axis]) * scale);
                for (int field = 4; field < fields.length; field++)
                    result.append(' ').append(fields[field]);
            }
            result.append('\n');
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String[] vertexFields(String line) {
        String trimmed = line.strip();
        if (trimmed.length() < 2
                || trimmed.charAt(0) != 'v'
                || !Character.isWhitespace(trimmed.charAt(1))) return null;
        String[] fields = trimmed.split("\\s+");
        if (fields.length < 4) throw new IllegalArgumentException("Incomplete gallery OBJ vertex");
        return fields;
    }

    private static float coordinate(String field) {
        float value = Float.parseFloat(field);
        if (!Float.isFinite(value))
            throw new IllegalArgumentException("Non-finite gallery OBJ vertex");
        return value;
    }

    private static void validate(
            ObjModel3D model, float targetHeight, String source, int minimumParts) {
        AABBf b = model.getLocalBounds();
        float width = b.maxX - b.minX, depth = b.maxY - b.minY;
        float tolerance = 1e-5f * Math.max(targetHeight, Math.max(width, depth));
        if (!b.isValid()
                || !(width > 0)
                || !(depth > 0)
                || !Float.isFinite(width)
                || !Float.isFinite(depth)
                || !Float.isFinite(b.maxZ)
                || Math.abs(b.minX + b.maxX) > tolerance
                || Math.abs(b.minY + b.maxY) > tolerance
                || Math.abs(b.minZ) > tolerance
                || Math.abs(b.maxZ - targetHeight) > tolerance
                || model.getTriangleCount() == 0)
            throw new IllegalStateException("Invalid normalized gallery bounds: " + source);
        int partTriangles = 0;
        for (ObjModel3D.Part part : model.getParts())
            partTriangles += part.model().getTriangleCount();
        if (partTriangles != model.getTriangleCount() || model.getParts().size() < minimumParts)
            throw new IllegalStateException("Missing gallery material groups: " + source);
        for (Model3D.Triangle triangle : model.getTriangles()) {
            for (Vector3f normal :
                    new Vector3f[] {
                        triangle.getNormalA(), triangle.getNormalB(), triangle.getNormalC()
                    }) {
                if (!normal.isFinite() || Math.abs(normal.lengthSquared() - 1) > 1e-4f)
                    throw new IllegalStateException("Invalid gallery surface normal: " + source);
            }
        }
    }

    @Override
    public void close() {
        if (closed) return;
        for (Entry entry : entries) entry.model().dispose();
        closed = true;
    }

    /** CPU-only asset verification, suitable for a headless build or packaged example. */
    public static void main(String[] args) {
        try (PhysicsStudioModels gallery = new PhysicsStudioModels()) {
            for (Entry entry : gallery.entries()) {
                System.out.printf(
                        Locale.ROOT,
                        "%s: %d triangles, %d materials, %.3f x %.3f x %.3f%n",
                        entry.name(),
                        entry.model().getTriangleCount(),
                        entry.model().getParts().size(),
                        entry.width(),
                        entry.depth(),
                        entry.height());
            }
        }
    }
}
