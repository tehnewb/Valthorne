package valthorne.ui.nodes.nano;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * NanoVG variant. Horizontal row of named command menus using the root's modal popup routing.
 * Each heading is a normal themed button with keyboard activation; each popup
 * supports arrows and Escape. Left/Right switches open menus, skipping disabled
 * headings and empty menus. Only one menu owned by this bar opens at a time.
 * <pre>{@code
 * NanoMenuBar bar = new NanoMenuBar();
 * bar.addMenu("File", List.of(new NanoPopupMenu.Item("Save", this::save, true)));
 * root.add(bar);
 * }</pre>
 * Heading and command callbacks execute on the UI thread. Removing the bar closes
 * all its overlays. Application shortcuts and nested submenu trees remain separate.
 * @author Albert Beaupre
 */
public class NanoMenuBar extends NanoContainer {
    private final List<NanoPopupMenu> menus = new ArrayList<>(); // Owned command popups parallel to heading buttons.
    private final List<NanoButton> headings = new ArrayList<>(); // Owned, ordered menu-heading controls.

    /**
     * Creates an empty horizontal bar with a fixed default height of 36 layout units.
     */
    public NanoMenuBar() { getLayout().row().height(36).noShrink(); }

    /**
     * Appends a heading with an immutable command snapshot. An empty command list
     * creates a heading that cannot open a popup; labels must be nonnull.
     * @param text heading text
     * @param items menu commands
     * @return this bar
     */
    public NanoMenuBar addMenu(String text, List<NanoPopupMenu.Item> items) {
        Objects.requireNonNull(text);
        NanoPopupMenu menu = new NanoPopupMenu().items(items);
        int index = menus.size();
        menu.horizontalNavigation(direction -> switchMenu(index, direction));
        menu.outsidePress(this::switchOnHeadingPress);
        NanoButton heading = new NanoButton(text).action(button -> {
            boolean wasOpen = menu.isOpen(); closeMenus();
            if (!wasOpen) menu.showBelow(button);
        });
        heading.getLayout().minWidth(64).heightPercent(100).padding(8).noShrink();
        menus.add(menu); headings.add(heading); add(heading); return this;
    }

    /**
     * Borrows a heading for theme, focus, or disabled-state configuration.
     * @param index zero-based heading index
     * @return owned heading; do not reparent it
     */
    public NanoButton getHeading(int index) { return headings.get(index); }

    /**
     * Borrows the corresponding popup so commands can be replaced between openings.
     * @param index zero-based heading index
     * @return owned popup
     */
    public NanoPopupMenu getMenu(int index) { return menus.get(index); }

    /**
     * Closes all menus owned by this bar without changing their command lists.
     */
    public void closeMenus() { menus.forEach(NanoPopupMenu::close); }

    /**
     * Opens the next available heading in circular order, closing prior overlays first.
     * @param from index of the currently open menu
     * @param direction -1 for previous, 1 for next
     */
    private void switchMenu(int from, int direction) {
        for (int offset = 1; offset <= menus.size(); offset++) {
            int index = Math.floorMod(from + direction * offset, menus.size());
            if (headings.get(index).isEnabled() && !menus.get(index).getItems().isEmpty()) {
                closeMenus(); menus.get(index).showBelow(headings.get(index)); return;
            }
        }
    }

    /** Handles a pointer press on another heading while the popup shield is active. */
    private boolean switchOnHeadingPress(valthorne.event.events.MousePressEvent press) {
        for (int i = 0; i < headings.size(); i++) {
            NanoButton heading = headings.get(i);
            if (!heading.contains(press.getX(), press.getY())) continue;
            boolean canOpen = heading.isEnabled() && !menus.get(i).getItems().isEmpty();
            boolean alreadyOpen = menus.get(i).isOpen();
            closeMenus();
            if (canOpen && !alreadyOpen) menus.get(i).showBelow(heading);
            return true;
        }
        return false;
    }

    /**
     * Dismisses root overlays when this bar is detached; children follow normal teardown.
     */
    @Override public void onDestroy() { closeMenus(); }
}
