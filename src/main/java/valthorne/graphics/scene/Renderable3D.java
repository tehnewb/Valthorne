package valthorne.graphics.scene;

import valthorne.graphics.model.Material3D;
import valthorne.graphics.render.ModelBatch3D;

import org.joml.Vector3f;
import org.joml.primitives.AABBf;
import valthorne.camera.Camera3D;

/**
 * Supplies material, visibility and sorting information to {@link ModelBatch3D}.
 * A directly submitted implementation must also implement {@link MeshRenderable3D}
 * or {@link BillboardRenderable3D} to define how geometry is emitted; this base
 * interface alone is not a supported drawing backend.
 *
 * <p>Submission always checks explicit visibility. Camera-dependent visibility
 * is queried only when frustum culling is enabled, so implementations must keep
 * an application's hide/show flag in {@link #isRenderableVisible()} instead of
 * relying solely on {@link #isVisible(Camera3D)}.</p>
 *
 * <p>Bounds and sort depth use world coordinates and the active camera's forward
 * direction. Custom renderables may be retained by reference until batch end;
 * keep their geometry and texture selection stable between submission and emission.
 * Resource ownership remains with the implementation or its caller.</p>
 *
 * @author Albert Beaupre
 */
public interface Renderable3D {

    /**
     * Provides the rendering settings used to classify and draw this object.
     * The model batch substitutes its default material when this method returns
     * null and caches a copy per material identity during a batch. Do not rely
     * on later material mutations changing an already queued submission.
     *
     * @return this object's material, or null to use the model batch's default
     */
    Material3D getMaterial();

    /**
     * Evaluates camera-dependent visibility, normally by testing current world
     * bounds against the camera frustum. The model batch passes its active camera
     * and skips this method entirely when culling is disabled. Implementations
     * may also reject missing geometry or other conditions that prevent drawing.
     *
     * @param camera the active, rebuilt camera used for the visibility test
     * @return whether the object should survive camera-dependent culling
     */
    boolean isVisible(Camera3D camera);

    /**
     * Reports explicit application-controlled visibility independently of the
     * camera. The default permits rendering; implementations with a hide/show
     * flag should return that flag so hiding works even with culling disabled.
     *
     * @return whether this object is eligible for submission before culling
     */
    default boolean isRenderableVisible() {
        return true;
    }

    /**
     * Provides optional current axis-aligned world bounds. The default has no
     * bounds; the default depth calculation uses the center of valid bounds.
     * An implementation may return internal mutable storage, so callers should
     * treat the result as borrowed and copy it before retaining or modifying it.
     *
     * @return world-space bounds, or null when unavailable
     */
    default AABBf getWorldBounds() {
        return null;
    }

    /**
     * Computes signed depth as the dot product of the camera's forward direction
     * and the vector from its position to the bounds center. With a normalized
     * direction, the result is measured in world units along the view axis;
     * positive values are in front of the camera, not radial distances.
     *
     * <p>Missing or invalid bounds return zero without accessing the camera.
     * Otherwise this default allocates a temporary center vector. Override it
     * for geometry without bounds or an allocation-free implementation. The model
     * batch sorts translucent and additive objects from larger to smaller depth.</p>
     *
     * @param camera the active camera providing position and forward direction
     * @return signed center depth, or zero when valid bounds are unavailable
     * @throws NullPointerException if camera is null and valid bounds are available
     */
    default float getSortDepth(Camera3D camera) {
        AABBf bounds = getWorldBounds();
        if (bounds == null || !(bounds.minX <= bounds.maxX && bounds.minY <= bounds.maxY && bounds.minZ <= bounds.maxZ))
            return 0f;
        Vector3f center = bounds.center(new Vector3f()).sub(camera.getPosition());
        Vector3f direction = camera.getDirection();
        return center.x() * direction.x() + center.y() * direction.y() + center.z() * direction.z();
    }
}
