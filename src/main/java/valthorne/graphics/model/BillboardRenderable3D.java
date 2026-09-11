package valthorne.graphics.model;

import valthorne.camera.Camera3D;
import valthorne.graphics.texture.TextureRegion;

/**
 * Defines textured billboard emission for a {@link Renderable3D}. The model
 * batch uses the region's texture to group compatible submissions and passes
 * the active camera when emitting geometry. Implementations supply their own
 * position, dimensions and orientation behavior through the emission callback.
 *
 * <p>A null region or a region without a texture causes {@link ModelBatch3D}
 * to reject the submission. Keep the region and backing texture stable until
 * batch end because the renderer reads them again while grouping queued objects.
 * The interface does not allocate, upload or dispose textures.</p>
 *
 * @author Albert Beaupre
 */
public interface BillboardRenderable3D extends Renderable3D {

    /**
     * Supplies the region whose backing texture is used for this submission's
     * billboard batch. The renderer borrows this object and does not transfer
     * texture ownership. Returning null marks the billboard as lacking drawable
     * texture data for model-batch submission.
     *
     * @return the active texture region, or null when no region is assigned
     */
    TextureRegion getTextureRegion();

    /**
     * Appends billboard geometry to a batch prepared for the texture returned
     * by {@link #getTextureRegion()}. Use the supplied camera to establish the
     * intended facing basis. Do not clear, reinitialize, render or dispose the
     * shared destination; the model batch owns those operations.
     *
     * @param billboardBatch the nonnull destination for compatible billboard vertices
     * @param camera         the active camera used to orient this object's geometry
     */
    void emit(BillboardBatch3D billboardBatch, Camera3D camera);
}
