package valthorne.ui;

/**
 * Represents an action that can be performed on a given node of type {@code N}.
 *
 * @param <N> the type of node that this action can be performed on, which extends {@code UINode}
 */
public interface NodeAction<N extends UINode> {

    /**
     * Performs an action on the specified node.
     *
     * @param node the node on which the action is performed must be of a type that extends {@code UINode}
     */
    void perform(N node);

}
