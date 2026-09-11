package valthorne.graphics.model;

/**
 * Supplies geometry on demand for a {@link ProceduralRenderable3D}. This callback
 * is invoked from the renderable's mesh-emission method and receives both the
 * shared destination and the owning renderable's current transform state.
 * The callback itself is responsible for generating and transforming vertices.
 *
 * <p>Use the owner's transform helpers to convert local points and normals to
 * world space before appending triangles. Keep its local bounds consistent with
 * the generated geometry so culling and transparent sorting remain meaningful.
 * The emitter does not control the batch lifetime and may be invoked again on
 * later frames or separate rendering passes.</p>
 *
 * @author Albert Beaupre
 */
@FunctionalInterface
public interface ProceduralMeshEmitter3D {

    /**
     * Generates and appends world-space geometry for the supplied owner. The
     * destination may already contain triangles from other compatible objects;
     * preserve them and leave rendering and disposal to the caller. Reading the
     * owner allows a reusable emitter to honor each instance's current transform.
     *
     * @param meshBatch  the prepared destination batch, borrowed for this callback
     * @param renderable the procedural owner whose geometry is being emitted
     */
    void emit(MeshBatch3D meshBatch, ProceduralRenderable3D renderable);
}
