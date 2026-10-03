package valthorne.ui.behavior;

/**
 * Immutable edit-history snapshot sharing the immutable text string. Endpoints
 * represent the selection before an edit and are restored without revalidation.
 *
 * <p>Snapshots carry text and selection together so undo and redo restore a coherent editing
 * position. They contain no widget rendering state, clipboard handle, or callback.</p>
 *
 * @param text   text at snapshot time
 * @param anchor fixed selection endpoint in UTF-16 units
 * @param caret  active selection endpoint in UTF-16 units
 * @author Albert Beaupre
 */
record TextEditState(String text, int anchor, int caret) {
}
