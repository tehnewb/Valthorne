package valthorne.ui.behavior;

/**
 * Reports a completed tree drag with the visible target row and insertion position.
 * The consumer owns the corresponding model change; the tree does not move nodes itself.
 *
 * @param source dragged tree node
 * @param target row under the pointer when the drag ended
 * @param position insertion position relative to the target row
 * @param <T> application value type
 */
public record TreeDrop<T>(TreeNode<T> source, TreeNode<T> target, TreeDropPosition position) {
}
