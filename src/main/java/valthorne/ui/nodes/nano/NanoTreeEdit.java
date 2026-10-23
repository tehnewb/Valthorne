package valthorne.ui.nodes.nano;

import valthorne.ui.behavior.*;

/**
 * Notification after an inline rename has been committed.
 * The node is borrowed from the tree model and text values are immutable snapshots.
 *
 * @param node renamed model node
 * @param oldText label before the edit
 * @param newText committed label
 * @param <T> application node value type
 */
public record NanoTreeEdit<T>(TreeNode<T> node, String oldText, String newText) {
}
