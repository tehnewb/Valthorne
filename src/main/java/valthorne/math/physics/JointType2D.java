package valthorne.math.physics;

/**
 * Identifies the planar relationship enforced by an owned joint. Distance joints
 * bound separation between two anchors; pivot joints keep a common anchor
 * coincident while allowing the bodies to rotate around it.
 */
public enum JointType2D {
    /**
     * Bounds separation between two body-local anchors initialized in world space.
     */
    DISTANCE,

    /**
     * Keeps a shared anchor coincident while permitting planar rotation.
     */
    PIVOT
}
