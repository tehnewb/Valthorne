package valthorne.graphics.model;

import org.joml.Vector3f;

/**
 * Describes a world-space model-triangle hit returned by
 * {@link Scene3D#pick(org.joml.primitives.Rayf)}. Scene picking selects the closest
 * finite hit among the tested model instances and visible node hierarchies;
 * this record itself performs no intersection or validation.
 *
 * <h2>Instance and Node Identity</h2>
 * <p>For a loose scene instance, {@code instance} is that live object and
 * {@code node} is null. For a scene-node hit, {@code node} is the original node
 * and {@code instance} is a temporary instance with the node's world transform
 * captured at query time. The temporary instance still shares model and material
 * references; it is not a deep copy of the node's resources.</p>
 *
 * <h2>Coordinates and Ownership</h2>
 * <p>The position is computed as ray origin plus direction multiplied by distance.
 * Distance is the ray parameter and equals world-space distance when direction
 * is normalized. The scene creates a new position vector, but this record retains
 * it directly and its generated accessor returns that same mutable vector.</p>
 *
 * <p>Record components are fixed references, not an immutable object graph.
 * Copy the position or instance state when a durable snapshot is needed. Direct
 * construction accepts null references and arbitrary distances without checks.</p>
 *
 * @param instance the hit loose instance or a transform snapshot for a node hit
 * @param node     the original scene node, or null for a loose-instance hit
 * @param distance the intersection parameter along the query ray
 * @param position the mutable world-space hit point retained without copying
 * @author Albert Beaupre
 */
public record PickResult3D(
        ModelInstance3D instance,
        SceneNode3D node,
        float distance,
        Vector3f position
) {
}
