package valthorne.graphics.model;

/**
 * Defines the mesh-emission backend of a {@link Renderable3D}. Implementations
 * append world-space triangle vertices to a batch supplied by {@link ModelBatch3D};
 * the model batch then renders accumulated geometry using the submission's material.
 * Object transforms must be applied by the implementation before vertices are emitted.
 *
 * <p>Emission is deferred until model-batch end. The callback supplies geometry,
 * while the owning batch controls clearing, drawing and resource lifetime.
 * Implementations should not change the shared batch's lifecycle or issue an
 * independent render while appending geometry.</p>
 *
 * @author Albert Beaupre
 */
public interface MeshRenderable3D extends Renderable3D {

    /**
     * Appends this object's world-space triangles to the prepared mesh batch.
     * Existing vertices may belong to other compatible submissions and must not
     * be cleared. Supply transformed normals and texture coordinates when using
     * an emission overload that accepts them. This method does not transfer
     * ownership of the batch or require the implementation to render it.
     *
     * @param meshBatch the nonnull destination prepared by the owning renderer
     */
    void emit(MeshBatch3D meshBatch);
}
