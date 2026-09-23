package valthorne.graphics.model;

import valthorne.graphics.render.RenderPass3D;
import valthorne.graphics.render.RenderStateSnapshot3D;

import org.joml.Vector2f;
import org.joml.Vector3f;
import valthorne.graphics.Color;
import valthorne.graphics.texture.Texture;
import valthorne.graphics.texture.TextureData;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.lwjgl.opengl.GL13;

/**
 * Loads Wavefront OBJ triangle geometry with UVs, explicit normals, optional
 * vertex colors, and a subset of MTL diffuse materials. Convex polygon faces are
 * triangulated as fans; concave polygons are not tessellated. Positive and relative
 * negative indices are supported. Object, group, and smoothing declarations do
 * not alter geometry grouping, which follows material names.
 * <p>
 * Loading decodes texture images on the CPU without allocating OpenGL textures.
 * First submission through a supporting renderer uploads them lazily. The model
 * owns decoded images and uploaded textures, while part materials expose those
 * textures as borrowed references. Dispose on the GL thread after upload; before
 * upload, disposal releases CPU image data only.
 * </p>
 * <p>
 * The convenience loader converts Y-up positions to Z-up, centers the X/Y footprint,
 * and places the lowest point at Z zero. Disable that option to retain source
 * coordinates. File references are resolved relative to their declaring OBJ or
 * MTL path; a custom resolver can supply bytes from another storage source.
 * </p>
 * <pre>{@code
 * ObjModel3D model = ObjModel3D.load("assets/scene.obj", false);
 * try {
 *     model.uploadTextures(); // With the rendering context current.
 *     // Submit the model through the scene's model rendering path.
 * } finally {
 *     model.dispose();
 * }
 * }</pre>
 *
 * @author Albert Beaupre
 */
public final class ObjModel3D extends Model3D {
    private final List<Part> parts; // Unmodifiable part list with borrowed mutable geometry/material components.
    private final Map<String, TextureData> images; // Owned decoded diffuse images keyed by resolved resource path.
    private final Map<Material3D, String> textureNames; // Identity-based part material bindings to decoded image paths.
    private final Map<String, Texture> textures = new HashMap<>(); // Owned lazily uploaded textures keyed by resource path.
    private boolean disposed; // Whether image and texture resource disposal completed.

    /**
     * Stores loaded geometry and takes ownership of image resources and texture
     * binding maps. Copies the part list but retains its mutable models/materials.
     *
     * @param triangles    combined geometry for the base model
     * @param parts        material-group geometry and material references
     * @param images       decoded images owned by this model
     * @param textureNames identity-based material-to-image mapping
     */
    private ObjModel3D(Triangle[] triangles, List<Part> parts, Map<String, TextureData> images,
                       Map<Material3D, String> textureNames) {
        super(triangles);
        this.parts = List.copyOf(parts);
        this.images = images;
        this.textureNames = textureNames;
    }

    /**
     * Loads a filesystem OBJ with Y-up to Z-up conversion, horizontal centering,
     * and grounding enabled. Geometry and image decoding are CPU-only.
     *
     * @param path OBJ filesystem path
     * @return newly owned model
     * @throws UncheckedIOException     if an OBJ, material, or image file cannot be read
     * @throws IllegalArgumentException if input geometry or supported material data is malformed
     */
    public static ObjModel3D load(String path) {
        return load(path, true);
    }

    /**
     * Loads an OBJ from a normalized absolute filesystem path and resolves its
     * dependencies relative to their declaring files.
     *
     * @param path             OBJ filesystem path
     * @param convertAndGround whether to swap Y/Z, center horizontally, and ground the model
     * @return newly owned model
     * @throws UncheckedIOException     if a required file cannot be read
     * @throws IllegalArgumentException if supported input data is malformed
     */
    public static ObjModel3D load(String path, boolean convertAndGround) {
        Path absolute = Path.of(path).toAbsolutePath().normalize();
        return load(absolute.toString(), name -> Files.readAllBytes(Path.of(name)), convertAndGround);
    }

    /**
     * Parses UTF-8 OBJ/MTL data through a caller-supplied byte resolver. Groups faces
     * by material, averages each triangle's source vertex colors, and supplies zero
     * UVs or generated face normals where references are omitted. Decodes each distinct
     * diffuse image once with vertical flipping enabled.
     * <p>
     * Supports MTL Kd, d, Tr, and map_Kd; texture-map options are rejected and other
     * directives are ignored. Failure disposes images decoded so far. OBJ parse errors
     * include the source path and line number.
     * </p>
     *
     * @param source           logical path used for loading and relative dependency resolution
     * @param resolver         nonnull provider of source and dependency bytes
     * @param convertAndGround whether to convert axes and reposition the model
     * @return newly owned geometry and image resources
     * @throws UncheckedIOException     if the resolver reports a read failure
     * @throws IllegalArgumentException if references, numbers, faces, or supported materials are invalid
     */
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
                        default -> {
                            // Object/group/smoothing metadata does not alter explicit vertex normals.
                        }
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

    /**
     * Converts a triangle from Y-up to grounded Z-up coordinates. Swapping axes
     * reverses handedness, so it also reverses vertex order while preserving matching
     * UV and normal assignments.
     *
     * @param t      source triangle
     * @param offset source-space centering and grounding offset
     * @return converted triangle
     */
    private static Triangle convert(Triangle t, Vector3f offset) {
        // Swapping Y/Z reverses handedness, so reverse the face order as well.
        return new Triangle(convertPosition(t.a(), offset), convertPosition(t.c(), offset), convertPosition(t.b(), offset), t.color(),
                t.uvA(), t.uvC(), t.uvB(), swap(t.normalA()), swap(t.normalC()), swap(t.normalB()));
    }

    /**
     * Subtracts the source-space offset from the supplied working vector, then
     * returns a new vector with Y and Z exchanged.
     *
     * @param p      mutable position working copy
     * @param offset source-space translation to subtract
     * @return converted position
     */
    private static Vector3f convertPosition(Vector3f p, Vector3f offset) {
        return swap(p.sub(offset));
    }

    /**
     * Copies a vector with Y and Z exchanged; used for both positions and normals.
     *
     * @param p source vector
     * @return new vector containing X, Z, Y
     */
    private static Vector3f swap(Vector3f p) {
        return new Vector3f(p.x(), p.z(), p.y());
    }

    /**
     * Resolves an optional texture-coordinate index, allocating zero UVs when absent.
     *
     * @param values parsed UV list
     * @param i      resolved index, or a negative value for no UV
     * @return referenced UV or new zero vector
     */
    private static Vector2f uv(List<Vector2f> values, int i) {
        return i < 0 ? new Vector2f() : values.get(i);
    }

    /**
     * Resolves an optional normal reference. A missing normal returns null so
     * triangle construction can derive its face normal.
     *
     * @param values parsed normal list
     * @param i      resolved index, or a negative value for no explicit normal
     * @return referenced normal or null
     */
    private static Vector3f normal(List<Vector3f> values, int i) {
        return i < 0 ? null : values.get(i);
    }

    /**
     * Parses three finite coordinates following an OBJ directive token.
     *
     * @param p token array with coordinates at indices one through three
     * @return new coordinate vector
     * @throws IllegalArgumentException if a coordinate is malformed or nonfinite
     */
    private static Vector3f vector(String[] p) {
        return new Vector3f(number(p[1]), number(p[2]), number(p[3]));
    }

    /**
     * Parses a finite floating-point token, rejecting NaN and infinities.
     *
     * @param s numeric token
     * @return finite parsed value
     * @throws IllegalArgumentException if parsing fails or the value is nonfinite
     */
    private static float number(String s) {
        float value = Float.parseFloat(s);
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite number");
        return value;
    }

    /**
     * Parses a position/UV/normal face reference, preserving omitted UV and normal
     * components as -1. Validates each present index against its current source list.
     *
     * @param s  slash-separated face token
     * @param pc number of parsed positions
     * @param tc number of parsed UVs
     * @param nc number of parsed normals
     * @return resolved zero-based references
     * @throws IllegalArgumentException if the token or an index is invalid
     */
    private static Ref ref(String s, int pc, int tc, int nc) {
        String[] p = s.split("/", -1);
        if (p.length > 3) throw new IllegalArgumentException("Invalid face reference: " + s);
        return new Ref(index(p[0], pc), p.length > 1 && !p[1].isEmpty() ? index(p[1], tc) : -1, p.length > 2 && !p[2].isEmpty() ? index(p[2], nc) : -1);
    }

    /**
     * Resolves an OBJ one-based positive index or a negative index relative to the
     * current list end. Zero and out-of-range references are invalid.
     *
     * @param s     integer index token
     * @param count current source-list size
     * @return zero-based list index
     * @throws IllegalArgumentException if the token is invalid, zero, or out of range
     */
    private static int index(String s, int count) {
        int raw = Integer.parseInt(s), index = raw > 0 ? raw - 1 : count + raw;
        if (raw == 0 || index < 0 || index >= count) throw new IllegalArgumentException("OBJ index out of range: " + s);
        return index;
    }

    /**
     * Removes text starting at the first hash character and trims surrounding
     * whitespace. Used before splitting OBJ and MTL directives.
     *
     * @param s raw source line
     * @return content without comment or surrounding whitespace
     */
    private static String strip(String s) {
        int i = s.indexOf('#');
        return (i < 0 ? s : s.substring(0, i)).trim();
    }

    /**
     * Resolves a dependency as a sibling of its declaring source, normalizes dot
     * segments, and converts separators to forward slashes for the resolver.
     *
     * @param source   declaring OBJ or MTL path
     * @param relative dependency path
     * @return normalized dependency path
     */
    private static String resolve(String source, String relative) {
        return Path.of(source).resolveSibling(relative).normalize().toString().replace('\\', '/');
    }

    /**
     * Reads UTF-8 material declarations into a name-keyed map. Repeated names reuse
     * their existing material information. Handles diffuse RGB, opacity/transparency,
     * and diffuse texture paths; ignores unsupported directives other than map options,
     * which are rejected.
     *
     * @param source    material-library path
     * @param resolver  byte provider for the library
     * @param materials mutable material-name map to populate
     * @throws IOException              if the library cannot be read
     * @throws IllegalArgumentException if a supported value is malformed or texture-map options are used
     */
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
                default -> {
                }
            }
        }
    }

    /**
     * Returns the unmodifiable material-group list. Its models and materials remain
     * mutable borrowed objects; uploaded textures are owned by this parent model.
     *
     * @return material parts in first face-group encounter order
     */
    public List<Part> getParts() {
        return parts;
    }

    /**
     * Uploads missing decoded images and binds the resulting owned textures to
     * part materials. Repeated calls reuse uploads and restore the original material
     * texture assignments, even if a caller changed those assignments. New uploads
     * run under a render-state snapshot; a fully uploaded model only reapplies Java
     * material references.
     *
     * @throws IllegalStateException if this model has been disposed
     */
    public void uploadTextures() {
        if (disposed) throw new IllegalStateException("OBJ model is disposed");
        if (textureNames.isEmpty()) return;
        if (textures.size() == images.size()) {
            // Material bindings can be edited by callers, so restore them on every
            // submission; already uploaded textures require no OpenGL state work.
            for (var entry : textureNames.entrySet()) entry.getKey().setTexture(textures.get(entry.getValue()));
            return;
        }
        try (RenderStateSnapshot3D ignored = new RenderStateSnapshot3D()) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            for (var entry : textureNames.entrySet()) {
                Texture texture = textures.computeIfAbsent(entry.getValue(), key -> new Texture(images.get(key)));
                entry.getKey().setTexture(texture);
            }
        }
    }

    /**
     * Releases all uploaded textures and decoded images, then marks the model
     * disposed. Repeated calls after successful disposal have no effect. Geometry
     * and part references remain accessible, but their former texture resources
     * must no longer be used. Requires the GL context if textures were uploaded.
     */
    public void dispose() {
        if (disposed) return;
        for (Texture texture : textures.values()) texture.dispose();
        for (TextureData data : images.values()) data.dispose();
        textures.clear();
        images.clear();
        disposed = true;
    }

    /**
     * Supplies owned byte arrays for logical OBJ, MTL, and image paths. The loader
     * passes normalized relative dependencies through the same resolver, allowing
     * filesystem, archive, or application-managed asset storage.
     *
     * @author Albert Beaupre
     */
    @FunctionalInterface
    public interface Resolver {
        /**
         * Reads the complete bytes for a logical resource. The loader decodes OBJ/MTL
         * bytes as UTF-8 and passes image bytes to the texture decoder.
         *
         * @param path logical resource path
         * @return nonnull complete resource bytes
         * @throws IOException if the resource cannot be read
         */
        byte[] read(String path) throws IOException;
    }

    /**
     * A material group from the loaded OBJ. Components are retained by reference;
     * the parent model owns any textures it later assigns to the material.
     *
     * <p>The record groups geometry and material without adding an independent disposal operation.
     * Changing the material affects consumers of that same part; retaining a part does not
     * extend the lifetime of textures owned by the parent model.</p>
     *
     * @param model    geometry for faces using this material group
     * @param material mutable diffuse material for the group
     * @author Albert Beaupre
     */
    public record Part(Model3D model, Material3D material) {
    }

    /**
     * Resolved zero-based vertex references for one OBJ face corner.
     *
     * <p>Indices address the separately parsed position, texture-coordinate, and normal lists.
     * Omitted texture coordinates or normals use minus one; this record stores indices only
     * and does not copy vertex data.</p>
     *
     * @param p required position index
     * @param t UV index, or -1 when omitted
     * @param n normal index, or -1 when omitted
     * @author Albert Beaupre
     */
    private record Ref(int p, int t, int n) {
    }

    /**
     * Mutable CPU material information collected from an MTL library before part
     * materials and decoded images are created. Unspecified diffuse color starts
     * opaque white and an unspecified texture remains absent.
     *
     * @author Albert Beaupre
     */
    private static final class Info {
        Color color = Color.WHITE.copy(); // Mutable diffuse RGBA parsed from Kd, d, and Tr directives.
        String texture; // Resolved diffuse image path, or null when absent.
    }
}
