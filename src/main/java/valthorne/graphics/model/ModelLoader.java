package valthorne.graphics.model;

import valthorne.asset.AssetLoader;

/**
 * Adapts {@link ModelParameters} to CPU-side OBJ loading for the asset system.
 * Each invocation delegates to {@link ObjModel3D#load(String, ObjModel3D.Resolver, boolean)}
 * to parse geometry and material definitions and decode referenced diffuse images.
 * Loading requires no OpenGL context; GPU textures are uploaded separately when
 * the model is submitted to a supported batch or explicitly uploaded on the render thread.
 *
 * <p>This loader holds no cache or mutable fields. Direct calls create separate
 * model instances; {@link valthorne.asset.Assets} handles asset-key lookup and
 * asynchronous scheduling when used as the entry point. Concurrency safety of
 * a custom resolver remains the caller's responsibility.</p>
 *
 * <p>The returned model owns decoded image resources and any subsequently
 * uploaded textures. Arrange for {@link ObjModel3D#dispose()} when those resources
 * are no longer needed; after upload, disposal must occur on the GL thread.
 * Neither parameter construction nor loading automatically uploads textures.</p>
 *
 * @author Albert Beaupre
 * @see ModelParameters
 */
public final class ModelLoader implements AssetLoader<ModelParameters, ObjModel3D> {
    /**
     * Loads one OBJ and its referenced resources using the supplied resolver and
     * coordinate-conversion setting. This call is synchronous; use the asset
     * manager's asynchronous API when loading should occur on a worker thread.
     * The parameter key is not used by this method, and no result is cached here.
     *
     * <p>Resolver I/O errors are wrapped by the OBJ loader. Invalid OBJ content
     * can produce an argument error, with source and line information for errors
     * encountered while parsing an OBJ statement. Other resolver or decoder
     * runtime failures propagate. The OBJ loader releases images it has decoded
     * if loading fails.</p>
     *
     * @param parameters the nonnull source, resolver and conversion configuration
     * @return a newly loaded model owning its decoded CPU image resources, with
     * GPU texture upload deferred
     * @throws NullPointerException         if parameters is null
     * @throws java.io.UncheckedIOException if the resolver throws an I/O exception
     * @throws IllegalArgumentException     if OBJ parsing rejects the content or no
     *                                      triangles are produced
     */
    @Override
    public ObjModel3D load(ModelParameters parameters) {
        return ObjModel3D.load(parameters.source(), parameters.resolver(), parameters.convertAndGround());
    }
}
