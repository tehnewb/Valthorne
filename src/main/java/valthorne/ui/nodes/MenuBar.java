package valthorne.ui.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Horizontal row of named command menus using the root's modal popup routing.
 * Each heading is a normal themed button with keyboard activation; each popup
 * supports arrows and Escape. Left/Right switches open menus, skipping disabled
 * headings and empty menus. Only one menu owned by this bar opens at a time.
 * <pre>{@code
 * MenuBar bar = new MenuBar();
 * bar.addMenu("File", List.of(new PopupMenu.Item("Save", this::save, true)));
 * root.add(bar);
 * }</pre>
 * Heading and command callbacks execute on the UI thread. Removing the bar closes
 * all its overlays. Application shortcuts and nested submenu trees remain separate.
 * @author Albert Beaupre
 */
public class MenuBar extends Panel {
    private final List<PopupMenu> menus = new ArrayList<>(); // Owned command popups parallel to heading buttons.
    private final List<Button> headings = new ArrayList<>(); // Owned, ordered menu-heading controls.

    /**
     * Creates an empty horizontal bar with a fixed default height of 36 layout units.
     */
    public MenuBar() { getLayout().row().height(36).noShrink(); }

    /**
     * Appends a heading with an immutable command snapshot. An empty command list
     * creates a heading that cannot open a popup; labels must be nonnull.
     * @param text heading text
     * @param items menu commands
     * @return this bar
     */
    public MenuBar addMenu(String text, List<PopupMenu.Item> items) {
        Objects.requireNonNull(text);
        PopupMenu menu = new PopupMenu().items(items);
        int index = menus.size();
        menu.horizontalNavigation(direction -> switchMenu(index, direction));
        Button heading = new Button(text).action(button -> {
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
    public Button getHeading(int index) { return headings.get(index); }

    /**
     * Borrows the corresponding popup so commands can be replaced between openings.
     * @param index zero-based heading index
     * @return owned popup
     */
    public PopupMenu getMenu(int index) { return menus.get(index); }

    /**
     * Closes all menus owned by this bar without changing their command lists.
     */
    public void closeMenus() { menus.forEach(PopupMenu::close); }

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

    /**
     * Dismisses root overlays when this bar is detached; children follow normal teardown.
     */
    @Override public void onDestroy() { closeMenus(); }
}
