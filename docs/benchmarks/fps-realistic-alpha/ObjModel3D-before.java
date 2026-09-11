package valthorne.graphics.model;

import valthorne.graphics.Color;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureData;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * OBJ geometry with UVs, normals and MTL diffuse materials. Loading is CPU-only.
 * Convex polygon faces are triangulated as fans. GPU textures are uploaded on first batch submission.
 * Dispose on the GL thread after uploading; otherwise disposal is CPU-only.
 */
public final class ObjModel3D extends Model3D {
    private final List<Part> parts;
    private final Map<String, TextureData> images;
    private final Map<Material3D, String> textureNames;
    private final Map<String, Texture> textures = new HashMap<>();
    private boolean disposed;
    private ObjModel3D(Triangle[] triangles, List<Part> parts, Map<String, TextureData> images,
                       Map<Material3D, String> textureNames) {
        super(triangles);
        this.parts = List.copyOf(parts);
        this.images = images;
        this.textureNames = textureNames;
    }

    /**
     * Legacy convenience: convert Y-up to Z-up, center horizontally, and place the base at zero.
     */
    public static ObjModel3D load(String path) {return load(path, true);}

    public static ObjModel3D load(String path, boolean convertAndGround) {
        Path absolute = Path.of(path).toAbsolutePath().normalize();
        return load(absolute.toString(), name -> Files.readAllBytes(Path.of(name)), convertAndGround);
    }

    public static ObjModel3D load(String source, Resolver resolver, boolean convertAndGround) {
        Map<String, TextureData> images = new LinkedHashMap<>();
        try {
            List<Vector3f> positions = new ArrayList<>(), normals = new ArrayList<>();
            List<Color> colors = new ArrayList<>();
            List<Vector2f> uvs = new ArrayList<>();
            Map<String, Info> materials = new LinkedHashMap<>();
            Map<String, List<Triangle>> groups = new LinkedHashMap<>();
            String current = "";
            int lineNumber = 0;
            for (String raw : new String(resolver.read(source), StandardCharsets.UTF_8).split("\\R")) {
                lineNumber++;
                String line = strip(raw);
                if (line.isEmpty()) continue;
                String[] p = line.split("\\s+");
                try {
                    switch (p[0]) {
                        case "v" -> {
                            positions.add(vector(p));
                            colors.add(p.length >= 7 ? new Color(number(p[4]), number(p[5]), number(p[6]), 1f) : Color.WHITE.copy());
                        }
                        case "vt" -> uvs.add(new Vector2f(number(p[1]), p.length > 2 ? number(p[2]) : 0));
                        case "vn" -> {
                            Vector3f normal = vector(p);
                            if (normal.lengthSquared() != 0f) normal.normalize();
                            normals.add(normal);
                        }
                        case "mtllib" -> {
                            for (int i = 1; i < p.length; i++) readMtl(resolve(source, p[i]), resolver, materials);
                        }
                        case "usemtl" -> current = line.substring(p[0].length()).trim();
                        case "f" -> {
                            if (p.length < 4) throw new IllegalArgumentException("Face needs at least three vertices");
                            Ref[] refs = new Ref[p.length - 1];
                            for (int i = 1; i < p.length; i++)
                                refs[i - 1] = ref(p[i], positions.size(), uvs.size(), normals.size());
                            List<Triangle> group = groups.computeIfAbsent(current, key -> new ArrayList<>());
                            for (int i = 1; i < refs.length - 1; i++) {
                                Ref a = refs[0], b = refs[i], c = refs[i + 1];
                                Color ca = colors.get(a.p), cb = colors.get(b.p), cc = colors.get(c.p);
                                Color color = new Color((ca.r() + cb.r() + cc.r()) / 3, (ca.g() + cb.g() + cc.g()) / 3, (ca.b() + cb.b() + cc.b()) / 3, 1);
                                group.add(new Triangle(positions.get(a.p), positions.get(b.p), positions.get(c.p), color,
                                        uv(uvs, a.t), uv(uvs, b.t), uv(uvs, c.t), normal(normals, a.n), normal(normals, b.n), normal(normals, c.n)));
                            }
                        }
                        default -> { /* Object/group/smoothing metadata does not alter explicit vertex normals. */ }
                    }
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException(source + ":" + lineNumber + ": " + line, e);
                }
            }
            if (groups.isEmpty()) throw new IllegalArgumentException("OBJ contained no triangles: " + source);
            Vector3f offset = new Vector3f();
            if (convertAndGround) {
                float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX, maxX = Float.NEGATIVE_INFINITY, maxZ = maxX;
                for (Vector3f v : positions) {
                    minX = Math.min(minX, v.x());
                    maxX = Math.max(maxX, v.x());
                    minY = Math.min(minY, v.y());
                    minZ = Math.min(minZ, v.z());
                    maxZ = Math.max(maxZ, v.z());
                }
                offset.set((minX + maxX) / 2, minY, (minZ + maxZ) / 2);
            }
            List<Part> parts = new ArrayList<>();
            List<Triangle> all = new ArrayList<>();
            Map<Material3D, String> textureNames = new IdentityHashMap<>();
            for (var entry : groups.entrySet()) {
                List<Triangle> triangles = entry.getValue();
                if (convertAndGround) triangles = triangles.stream().map(t -> convert(t, offset)).toList();
                all.addAll(triangles);
                Info info = materials.getOrDefault(entry.getKey(), new Info());
                Material3D material = new Material3D().setTint(info.color);
                if (info.color.a() < 1f) material.setRenderPass(RenderPass3D.TRANSLUCENT);
                if (info.texture != null) {
                    if (!images.containsKey(info.texture))
                        images.put(info.texture, TextureData.load(resolver.read(info.texture), true));
                    textureNames.put(material, info.texture);
                }
                parts.add(new Part(new Model3D(triangles.toArray(Triangle[]::new)), material));
            }
            return new ObjModel3D(all.toArray(Triangle[]::new), parts, images, textureNames);
        } catch (IOException e) {
            images.values().forEach(TextureData::dispose);
            throw new UncheckedIOException("Failed to load OBJ: " + source, e);
        } catch (RuntimeException | Error e) {
            images.values().forEach(TextureData::dispose);
            throw e;
        }
    }

    private static Triangle convert(Triangle t, Vector3f offset) {
        // Swapping Y/Z reverses handedness, so reverse the face order as well.
        return new Triangle(convertPosition(t.getA(), offset), convertPosition(t.getC(), offset), convertPosition(t.getB(), offset), t.getColor(),
                t.getUvA(), t.getUvC(), t.getUvB(), swap(t.getNormalA()), swap(t.getNormalC()), swap(t.getNormalB()));
    }

    private static Vector3f convertPosition(Vector3f p, Vector3f offset) {return swap(p.sub(offset));}

    private static Vector3f swap(Vector3f p) {return new Vector3f(p.x(), p.z(), p.y());}

    private static Vector2f uv(List<Vector2f> values, int i) {return i < 0 ? new Vector2f() : values.get(i);}

    private static Vector3f normal(List<Vector3f> values, int i) {return i < 0 ? null : values.get(i);}

    private static Vector3f vector(String[] p) {return new Vector3f(number(p[1]), number(p[2]), number(p[3]));}

    private static float number(String s) {
        float value = Float.parseFloat(s);
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite number");
        return value;
    }

    private static Ref ref(String s, int pc, int tc, int nc) {
        String[] p = s.split("/", -1);
        if (p.length > 3) throw new IllegalArgumentException("Invalid face reference: " + s);
        return new Ref(index(p[0], pc), p.length > 1 && !p[1].isEmpty() ? index(p[1], tc) : -1, p.length > 2 && !p[2].isEmpty() ? index(p[2], nc) : -1);
    }

    private static int index(String s, int count) {
        int raw = Integer.parseInt(s), index = raw > 0 ? raw - 1 : count + raw;
        if (raw == 0 || index < 0 || index >= count) throw new IllegalArgumentException("OBJ index out of range: " + s);
        return index;
    }

    private static String strip(String s) {
        int i = s.indexOf('#');
        return (i < 0 ? s : s.substring(0, i)).trim();
    }

    private static String resolve(String source, String relative) {
        return Path.of(source).resolveSibling(relative).normalize().toString().replace('\\', '/');
    }

    private static void readMtl(String source, Resolver resolver, Map<String, Info> materials) throws IOException {
        Info current = null;
        for (String raw : new String(resolver.read(source), StandardCharsets.UTF_8).split("\\R")) {
            String line = strip(raw);
            if (line.isEmpty()) continue;
            String[] p = line.split("\\s+");
            if (p[0].equals("newmtl")) {
                current = materials.computeIfAbsent(line.substring(6).trim(), key -> new Info());
                continue;
            }
            if (current == null) continue;
            switch (p[0]) {
                case "Kd" -> current.color.set(number(p[1]), number(p[2]), number(p[3]), current.color.a());
                case "d", "Tr" ->
                        current.color.set(current.color.r(), current.color.g(), current.color.b(), p[0].equals("Tr") ? 1 - number(p[1]) : number(p[1]));
                case "map_Kd" -> {
                    String name = line.substring(6).trim();
                    if (name.startsWith("-"))
                        throw new IllegalArgumentException("MTL map options are not supported: " + line);
                    current.texture = resolve(source, name);
                }
                default -> {}
            }
        }
    }

    public List<Part> getParts() {return parts;}

    /**
     * Called on the render thread; repeated calls reuse uploaded textures.
     */
    public void uploadTextures() {
        if (disposed) throw new IllegalStateException("OBJ model is disposed");
        if (textureNames.isEmpty()) return;
        try (RenderStateSnapshot3D ignored = new RenderStateSnapshot3D()) {
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            for (var entry : textureNames.entrySet()) {
                Texture texture = textures.computeIfAbsent(entry.getValue(), key -> new Texture(images.get(key)));
                entry.getKey().setTexture(texture);
            }
        }
    }

    public void dispose() {
        if (disposed) return;
        for (Texture texture : textures.values()) texture.dispose();
        for (TextureData data : images.values()) data.dispose();
        textures.clear();
        images.clear();
        disposed = true;
    }

    @FunctionalInterface
    public interface Resolver {
        byte[] read(String path) throws IOException;
    }

    public record Part(Model3D model, Material3D material) {
    }

    private record Ref(int p, int t, int n) {
    }

    private static final class Info {
        Color color = Color.WHITE.copy();
        String texture;
    }
}
