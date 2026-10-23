package valthorne.ui.nodes;

/**
 * Transient row retaining its command index so Tab-driven focus and arrow-driven
 * highlighting agree. Rows are owned and recycled by the virtualized option list.
 * The immutable index belongs to one opening and is discarded when rows are cleared.
 * @author Albert Beaupre
 */
final class PopupMenuButton extends Button {
    private final PopupMenu owner; // Borrowed control coordinating this helper's behavior.

    final int index; // Command position represented by this transient row.

    /**
     * Creates a themed command label without executing its callback.
     * @param index valid position in the current command snapshot
     */
    PopupMenuButton(PopupMenu owner, int index) {
        super(owner.items.get(index).text());
        this.owner = owner;
 this.index = index;
    }
}
