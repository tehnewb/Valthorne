package valthorne.graphics.model;

import valthorne.asset.AssetParameters;
import valthorne.io.file.ValthorneFiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Describes an OBJ asset's source, asset-manager identity, byte resolver, and
 * optional coordinate conversion without performing any resource reads.
 * {@link ModelLoader} consumes these parameters to load geometry, material
 * definitions and decoded diffuse images on the CPU.
 *
 * <h2>Source and Asset Identity</h2>
 * <p>The source identifies the OBJ file for the resolver; the key identifies
 * the resulting asset in {@link valthorne.asset.Assets}. They need not be equal.
 * Filesystem factories normalize the source to an absolute path while preserving
 * the supplied key. Classpath sources are retained as supplied. The resolver is
 * also used for referenced MTL files and diffuse images, with dependency names
 * resolved relative to the referring file by {@link ObjModel3D}.</p>
 *
 * <h2>Coordinate Conversion</h2>
 * <p>Factories preserve OBJ coordinates by default. Enabling conversion centers
 * the input X/Z bounds, subtracts the minimum input Y, then swaps Y and Z to
 * produce a Z-up model grounded at zero. The loader also adjusts face ordering
 * and normals for that axis swap. No unit rescaling is performed.</p>
 *
 * <pre>{@code
 * ModelParameters parameters = ModelParameters.fromPath("models/tree.obj", "tree")
 *         .withConversion(true);
 * ObjModel3D model = new ModelLoader().load(parameters);
 * // Upload textures later on the render thread, and dispose the model when done.
 * }</pre>
 *
 * <p>The record is immutable, but retains the resolver by reference. A custom
 * resolver used for asynchronous loads must support the access pattern of those
 * loads. Constructing parameters neither checks resource existence nor uploads
 * GPU textures. Use distinct asset keys for variants that must coexist.</p>
 *
 * @param source           the nonblank OBJ location understood by the resolver
 * @param key              the nonblank identity used by the asset manager
 * @param resolver         the nonnull byte reader shared by the OBJ and its dependencies
 * @param convertAndGround whether to center, ground and convert Y-up input to Z-up
 * @author Albert Beaupre
 * @see ModelLoader
 */
public record ModelParameters(
        String source,
        String key,
        ObjModel3D.Resolver resolver,
        boolean convertAndGround
) implements AssetParameters {
    /**
     * Validates and retains the supplied loading configuration. Source and key
     * must contain non-whitespace characters; accepted strings are not trimmed or
     * normalized here. The resolver is retained without being invoked.
     *
     * @param source           the OBJ source name
     * @param key              the asset-manager identity
     * @param resolver         the byte reader for the source and referenced resources
     * @param convertAndGround whether loading should apply coordinate conversion
     * @throws IllegalArgumentException if source or key is null or blank
     * @throws NullPointerException     if the resolver is null after string validation
     */
    public ModelParameters {
        if (source == null || source.isBlank() || key == null || key.isBlank())
            throw new IllegalArgumentException("Source and key must not be blank");
        Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * Creates filesystem parameters using the original path string as the asset
     * key. The source becomes an absolute normalized path, but the key retains
     * its original spelling. Conversion is disabled and no file is read yet.
     *
     * @param path the filesystem OBJ path and nonblank asset key
     * @return a new configuration preserving OBJ coordinates
     * @throws NullPointerException               if path is null
     * @throws java.nio.file.InvalidPathException if path cannot be parsed
     * @throws IllegalArgumentException           if path is blank and therefore an invalid key
     */
    public static ModelParameters fromPath(String path) {return fromPath(path, path);}

    /**
     * Creates filesystem parameters with an explicit asset key. Relative paths
     * are resolved to absolute paths at construction and normalized without
     * resolving symbolic links or verifying file existence. The resolver reads
     * each requested file fully through {@link Files#readAllBytes(Path)} when
     * loading occurs. Coordinate conversion is initially disabled.
     *
     * @param path the OBJ filesystem path
     * @param key  the nonblank asset identity, independent of path normalization
     * @return a new filesystem-backed configuration
     * @throws NullPointerException               if path is null
     * @throws java.nio.file.InvalidPathException if path cannot be parsed
     * @throws IllegalArgumentException           if key is null or blank
     */
    public static ModelParameters fromPath(String path, String key) {
        return new ModelParameters(Path.of(path).toAbsolutePath().normalize().toString(), key,
                name -> Files.readAllBytes(Path.of(name)), false);
    }

    /**
     * Creates parameters whose resolver reads from the classpath through
     * {@link ValthorneFiles#readBytes(String)}. Keep referenced MTL files and
     * images at paths compatible with their relative references. The resource
     * name is stored unchanged; existence is checked only during loading.
     * Coordinate conversion is initially disabled.
     *
     * @param resource the nonblank OBJ path relative to the classpath root
     * @param key      the nonblank asset identity
     * @return a new classpath-backed configuration
     * @throws IllegalArgumentException if resource or key is null or blank
     */
    public static ModelParameters fromClasspath(String resource, String key) {
        return new ModelParameters(resource, key, ValthorneFiles::readBytes, false);
    }

    /**
     * Creates a configuration with the requested coordinate-conversion flag,
     * preserving the source, key and same resolver instance. The original record
     * is unchanged, and a new record is returned even if the flag already matches.
     * This does not reload an existing asset or assign a new cache identity.
     *
     * @param convertAndGround whether loading should center and ground Y-up input
     *                         and swap its Y/Z axes to produce Z-up coordinates
     * @return a new configuration sharing this record's source, key and resolver
     */
    public ModelParameters withConversion(boolean convertAndGround) {
        return new ModelParameters(source, key, resolver, convertAndGround);
    }
}
