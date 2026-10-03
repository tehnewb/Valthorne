package valthorne.ui.behavior;

/**
 * Describes where a dragged tree row will land relative to the row under the pointer.
 * A drop inside reparents the source; drops before or after preserve the target's parent.
 */
public enum TreeDropPosition {
    BEFORE,
    INSIDE,
    AFTER
}
