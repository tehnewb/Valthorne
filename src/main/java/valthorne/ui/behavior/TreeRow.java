package valthorne.ui.behavior;

/**
 * Immutable visible-tree row. The node is borrowed; depth is zero-based
 * indentation within this snapshot and does not mutate node ownership.
 *
 * @param <T> application value type
 * @param node borrowed visible node
 * @param depth zero-based indentation depth
 */
public record TreeRow<T>(TreeNode<T> node, int depth) {
}
