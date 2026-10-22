package valthorne.ui.nodes.nano;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import valthorne.event.events.MouseReleaseEvent;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Directory row supporting modifier selection and plain double-click activation.
 * The index refers to one immutable listing generation and is discarded on refresh.
 * @author Albert Beaupre
 */
final class NanoFileExplorerRow extends NanoButton {
    /**
     * Immutable metadata formatter reused across virtual row materialization.
     */
    private static final DateTimeFormatter MODIFIED_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final NanoFileExplorer owner; // Borrowed control coordinating this helper's behavior.

    final int index; // Position represented by this transient row.

    /**
     * Formats a cached entry name and reads modification metadata once for detail rows.
     *
     * @param owner borrowed explorer providing the current immutable listing
     * @param index valid current listing index
     */
    NanoFileExplorerRow(NanoFileExplorer owner, int index) {
        super(owner.model.getEntries().get(index).path().getFileName().toString());
        this.owner = owner;

        this.index = index;
        action(button -> owner.select(index));
        setStyleName("chooser-row");
        leftAligned(true);
        setStyle(NanoButton.PADDING_X_KEY, 32f);
        if (owner.details) {
            getLabel()
                    .setVisible(false);
            NanoScrollPanel name = NanoWidgetSupport.viewport();
            name.horizontal(false)
                    .vertical(false)
                    .horizontalBar(false)
                    .verticalBar(false);
            name.setClickable(false);
            name.getLayout()
                    .absolute()
                    .left(30)
                    .top(0)
                    .widthPercent(48)
                    .heightPercent(100);
            NanoLabel text = new NanoLabel(owner.model.getEntries().get(index).path().getFileName().toString());
            text.setClickable(false);
            text.getLayout()
                    .marginTop(6);
            name.setContent(text);
            add(name);
            NanoFileIcon icon = new NanoFileIcon(owner.model.getEntries().get(index).directory());
            icon.getLayout()
                    .absolute()
                    .left(9)
                    .top(5)
                    .width(16)
                    .height(18);
            add(icon);
            String date;
            try {
                date = MODIFIED_DATE.format(Files.getLastModifiedTime(owner.model.getEntries().get(index).path()).toInstant().atZone(ZoneId.systemDefault()));
            } catch (IOException | SecurityException failure) {
                date = "Unavailable";
            }
            NanoLabel modified = new NanoLabel(date);
            modified.setClickable(false);
            modified.getLayout()
                    .absolute()
                    .leftPercent(57)
                    .top(6);
            add(modified);
        } else {
            NanoFileIcon icon = new NanoFileIcon(owner.model.getEntries().get(index).directory());
            icon.getLayout()
                    .absolute()
                    .left(9)
                    .top(7)
                    .width(16)
                    .height(18);
            add(icon);
        }
    }

    /**
     * Selects on an accepted primary release, honoring range/toggle modifiers.
     * Two plain releases on the same entry within 500 ms activate it; modifier
     * selection resets double-click tracking and never opens an entry.
     * @param event routed mouse release
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        if (!isActivationRelease(event) || owner.isDisabled()) return;
        event.consume();
        boolean twice = owner.model.click(index, event.isShiftDown(), event.isCtrlDown() || event.isSuperDown(), System.nanoTime());
        owner.list.revealIndex(index);
        if (twice) owner.openSelection();
    }
}
