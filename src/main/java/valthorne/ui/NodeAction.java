package valthorne.ui;

/**
 * Represents an action that can be performed on a given node of type {@code N}.
 * Implementations receive the node itself and may update its state. This contract
 * supplies no scheduling, exception handling, or automatic event consumption.
 *
 * @param <N> the type of node that this action can be performed on, which extends {@code UINode}
 * @author Albert Beaupre
 */
public interface NodeAction<N extends UINode> {

    /**
     * Performs an action on the specified node.
     * The caller chooses when and on which thread to invoke the action; implementations
     * that mutate UI state should follow the owning root's threading requirements.
     *
     * @param node the node on which the action is performed must be of a type that extends {@code UINode}
     */
    void perform(N node);

}
