package valthorne.ui.nodes;

import valthorne.ui.behavior.DirectoryBrowserModel;
import valthorne.ui.behavior.DirectoryBrowserModel.Entry;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.ui.UIInputEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Read-only local directory browser with breadcrumbs, parent/refresh/open commands,
 * hidden-file filtering, and virtualized directory rows. Click to select; Enter or
 * Open enters a directory or emits a file callback. Arrows and Home/End select rows;
 * Backspace requests the parent. Navigation never renames, deletes, or launches files.
 * <pre>{@code
 * FileExplorer files = new FileExplorer(Path.of("assets"));
 * files.filter(path -> path.toString().endsWith(".png"));
 * files.onOpen(path -> loadTexture(path));
 * root.add(files);
 * }</pre>
 * Construction and refresh enumerate directories synchronously on the caller's UI
 * thread. This is suitable for local asset browsing; network or very large directory
 * scans should use an application-managed asynchronous model. Entries are snapshots,
 * not a live filesystem watch. Directories always remain visible through file filters.
 * Navigation resolves real paths and, by default, stays beneath the construction root,
 * including symlink targets. The two-argument constructor can enable unrestricted
 * navigation. Optional multiple selection supports Ctrl/Super toggles and Shift ranges;
 * double-click activates a row. Use {@link FileChooser} for filename entry, file-type
 * filters, Open/Save approval, cancellation, and overwrite confirmation. Containment is
 * a browsing policy, not an operating-system security sandbox.
 * @author Albert Beaupre
 */
public class FileExplorer extends Panel {
    private final DirectoryBrowserModel model;
    private final BreadcrumbBar breadcrumbs = new BreadcrumbBar(); // Owned current-model.getDirectory() navigation.
    private final Label status = new Label(); // Owned current path, selection, or error text.
    private final Button up = new Button("Up"); // Owned parent-navigation button.
    private final VirtualList list; // Owned virtualized model.getDirectory() rows.
    private final Panel toolbar = new Panel(); // Optional standalone navigation commands.
    private final ScrollPanel trail = new ScrollPanel(); // Optional standalone breadcrumb viewport.
    private final ScrollPanel statusViewport = new ScrollPanel(); // Optional standalone status viewport.
    private final Panel header = new Panel(); // Detail column headings above the virtual rows.
    private boolean details; // Whether rows display file icons and metadata columns.
    private final Button nameHeading = new Button("Name  ^"); // Name ordering command and direction indicator.
    private final Button dateHeading = new Button("Date modified"); // Modification ordering command and direction indicator.

    /**
     * Opens and scans an existing directory, establishing its real path as the root.
     * @param root existing readable directory
     * @throws IOException if resolution or initial enumeration fails
     */
    public FileExplorer(Path root) throws IOException {
        this(root, true);
    }

    /**
     * Opens a directory with optional containment. Unconfined browsing supports
     * filesystem roots and arbitrary accessible locations, as needed by file choosers.
     * @param root initial existing readable directory
     * @param confined whether to restrict navigation to descendants of root
     * @throws IOException if resolution or initial enumeration fails
     */
    public FileExplorer(Path root, boolean confined) throws IOException {
        model = new DirectoryBrowserModel(root, confined);
        getLayout().column().minWidth(280).minHeight(160);
        toolbar.getLayout().row().height(36).noShrink();
        Button refresh = new Button("Refresh").action(button -> request(model.getDirectory()));
        Button activate = new Button("Open").action(button -> openSelection());
        up.action(button -> { if (model.canGoUp()) request(model.getDirectory().getParent()); });
        for (Button button : List.of(up, refresh, activate)) button.getLayout().width(72).heightPercent(100).noShrink();
        toolbar.add(up, refresh, activate);
        breadcrumbs.onNavigate(this::request);
        trail.horizontal(true).vertical(false);
        trail.getLayout().height(36).noShrink(); trail.setContent(breadcrumbs);
        list = new VirtualList(0, index -> {
            Button row = new FileRow(index);
            row.setSelected(model.isSelected(index)); return row;
        });
        list.rowHeight(32).gap(0); list.getLayout().height(0).grow(1).shrink(1).minHeight(0).widthPercent(100);
        statusViewport.horizontal(false).vertical(false).horizontalBar(false).verticalBar(false);
        statusViewport.getLayout().height(28).widthPercent(100).noShrink();
        statusViewport.setContent(status);
        header.getLayout().row().height(0).noShrink(); header.setVisible(false);
        nameHeading.action(button -> requestSort(false)); dateHeading.action(button -> requestSort(true));
        nameHeading.setStyleName("chooser-heading"); nameHeading.getLayout().widthPercent(56).heightPercent(100).itemsStart().paddingLeft(12);
        dateHeading.setStyleName("chooser-heading"); dateHeading.getLayout().widthPercent(44).heightPercent(100).itemsStart().paddingLeft(4);
        header.add(nameHeading, dateHeading);
        add(toolbar, trail, header, list, statusViewport);
        model.bindView(this::synchronizeListing, this::synchronizeSelection, failure -> status.text(failure.getMessage()));
        synchronizeListing();
    }

    /**
     * Hides duplicate navigation chrome when another component supplies path and actions.
     * The listing and selection remain intact; standalone explorers show chrome by default.
     * @param visible whether toolbar, breadcrumbs, and status occupy layout space
     * @return this explorer
     */
    public FileExplorer chrome(boolean visible) {
        toolbar.setVisible(visible); toolbar.getLayout().height(visible ? 36 : 0);
        trail.setVisible(visible); trail.getLayout().height(visible ? 36 : 0);
        statusViewport.setVisible(visible); statusViewport.getLayout().height(visible ? 28 : 0); return this;
    }

    /**
     * Chooses compact, left-aligned icon/name/date rows or the original simple listing.
     * Metadata is read when a virtual row is created and refreshed with the directory.
     * @param details whether the detail header and metadata are shown
     * @return this explorer
     */
    public FileExplorer details(boolean details) {
        this.details = details; setStyleName(details ? "chooser-pane" : "");
        header.setVisible(details); header.getLayout().height(details ? 30 : 0);
        list.rowHeight(details ? 28 : 32); list.refreshItems(); return this;
    }

    /**
     * Filters names of both files and folders within the current directory. This is
     * a case-insensitive substring search, not a recursive filesystem scan.
     * @param text search string; empty restores the normal filtered listing
     * @return this explorer
     * @throws IOException if enumeration fails, leaving the old query and listing intact
     */
    public FileExplorer search(String text) throws IOException {
        model.search(text);
        return this;
    }

    /**
     * Exposes the owned virtual row viewport for focus, inspection, and accessibility.
     * @return listing viewport; its child factory belongs to this explorer
     */
    public VirtualList getList() { return list; }

    /**
     * Changes detail-row ordering after a successful scan, always keeping folders first.
     * Missing metadata sorts as the oldest timestamp; ties use stable filename order.
     * @param modified true for modification time, false for filename
     * @param descending true for descending order within each folder/file group
     * @return this explorer
     * @throws IOException if the rescan fails, retaining prior ordering and listing
     */
    public FileExplorer sort(boolean modified, boolean descending) throws IOException {
        model.sort(modified, descending);
        updateSortHeadings();
        return this;
    }

    /**
     * Toggles a column's direction or starts a newly chosen column in ascending order.
     * @param modified whether the modification-date heading was activated
     */
    private void requestSort(boolean modified) {
        try { sort(modified, model.isSortModified() == modified && !model.isDescending()); }
        catch (IOException failure) { failed(failure); }
    }

    /**
     * Navigates atomically after a successful scan. Relative paths resolve against the
     * current directory; failures leave directory, entries, and selection unchanged.
     * @param path absolute or current-directory-relative destination
     * @return this explorer
     * @throws IOException if resolution, containment, or enumeration fails
     */
    public FileExplorer navigate(Path path) throws IOException {
        model.navigate(path);
        return this;
    }

    /**
     * Configures Ctrl/Super toggles and Shift ranges; disabling collapses existing membership.
     * @param multiple whether multiple entries may be selected
     * @return this explorer
     */
    public FileExplorer multipleSelection(boolean multiple) { model.multipleSelection(multiple); return this; }

    /**
     * Copies selected paths in current listing order. Directory and file entries are
     * both selectable; a chooser applies its final selection-type policy at approval.
     * @return immutable selected-path snapshot
     */
    public List<Path> getSelectedPaths() { return model.getSelectedPaths(); }

    /**
     * Replaces the selection callback; successful refreshes notify an empty selection.
     * @param listener nonnull synchronous listener
     * @return this explorer
     */
    public FileExplorer onSelectionChange(Consumer<List<Path>> listener) { model.onSelectionChange(listener); return this; }

    /**
     * Replaces the callback for actual successful directory changes. Filter-only refreshes
     * do not emit a directory event; the new listing is available before notification.
     * @param listener nonnull synchronous listener
     * @return this explorer
     */
    public FileExplorer onDirectoryChange(Consumer<Path> listener) { model.onDirectoryChange(listener); return this; }

    /**
     * Changes the file-only filter after successfully scanning with it. Directories
     * bypass the filter; callback failures propagate without committing a partial list.
     * @param filter nonnull predicate called synchronously for each visible file
     * @return this explorer
     * @throws IOException if the new scan fails
     */
    public FileExplorer filter(Predicate<Path> filter) throws IOException {
        model.filter(filter);
        return this;
    }

    /**
     * Rescans with a new hidden-file policy before committing it.
     * @param show whether hidden entries should be displayed
     * @return this explorer
     * @throws IOException if scanning fails
     */
    public FileExplorer showHidden(boolean show) throws IOException {
        model.showHidden(show);
        return this;
    }

    /**
     * Rescans the current directory using the active filter and hidden-file policy.
     * A successful refresh clears selection; failures preserve the previous snapshot.
     * @return this explorer
     * @throws IOException if the current directory can no longer be read or resolved
     */
    public FileExplorer refresh() throws IOException {
        model.refresh();
        return this;
    }

    /**
     * Reads the canonical current directory; no filesystem query occurs.
     * @return current absolute directory
     */
    public Path getDirectory() { return model.getDirectory(); }

    /**
     * Reads the immutable last successful directory snapshot.
     * @return entries in displayed order
     */
    public List<Entry> getEntries() { return model.getEntries(); }

    /**
     * Reads the selected path from the snapshot without resolving it again.
     * @return selected path or null when nothing is selected
     */
    public Path getSelected() { return model.getSelected(); }

    /**
     * Selects and reveals a row without opening it. Invalid indices leave state intact;
     * index -1 clears selection. Disabled explorers ignore selection requests.
     * @param index displayed row index or -1
     */
    public void select(int index) {
        select(index, false, false);
    }

    /**
     * Selects using optional Shift-range and Ctrl/Super-toggle semantics. The lead
     * is revealed without opening anything; -1 clears all membership.
     * @param index displayed row index or -1
     * @param extend whether to extend from the selection anchor
     * @param toggle whether to toggle membership when multiple selection is enabled
     */
    public void select(int index, boolean extend, boolean toggle) {
        if (isDisabled()) return;
        model.select(index, extend, toggle);
        if (index >= 0) list.scrollToIndex(index);
    }

    /**
     * Updates live row highlights and status before emitting an immutable path snapshot.
     * Virtualized rows created later read the same retained selection model.
     */
    private void synchronizeSelection() {
        for (int i = 0; i < model.getEntries().size(); i++) {
            var row = list.getItemNode(i); if (row != null) row.setSelected(model.isSelected(i));
        }
        int selected = model.getSelectedIndex();
        status.text(selected < 0 ? model.getDirectory().toString() : model.getEntries().get(selected).path().toString());
    }

    private void synchronizeListing() {
        list.itemCount(model.getEntries().size()); list.refreshItems(); list.scrollY(0);
        breadcrumbs.path(model.getBreadcrumbRoot(), model.getDirectory()); up.setEnabled(model.canGoUp());
        status.text(model.getDirectory().toString()); updateSortHeadings();
    }

    private void updateSortHeadings() {
        boolean modified = model.isSortModified(), descending = model.isDescending();
        nameHeading.text("Name" + (modified ? "" : descending ? "  v" : "  ^"));
        dateHeading.text("Date modified" + (!modified ? "" : descending ? "  v" : "  ^"));
    }

    /**
     * Revalidates selection, entering a directory or notifying the application for an
     * existing file. IO failures update status and notify onError; callback exceptions
     * propagate. No native application is launched and no filesystem data is changed.
     */
    public void openSelection() { if (!isDisabled()) model.openSelection(); }

    /**
     * Replaces the callback for file activation; directory navigation does not emit it.
     * @param listener nonnull callback receiving the current canonical file path
     * @return this explorer
     */
    public FileExplorer onOpen(Consumer<Path> listener) { model.onOpen(listener); return this; }

    /**
     * Replaces the listener for user-triggered IO failures. Direct navigate/filter calls
     * throw instead, allowing application code to choose its own error handling.
     * @param listener nonnull synchronous failure callback
     * @return this explorer
     */
    public FileExplorer onError(Consumer<IOException> listener) { model.onError(listener); return this; }

    /**
     * Attempts user navigation, retaining the old listing and displaying failures.
     * @param path requested directory
     */
    private void request(Path path) {
        if (isDisabled()) return;
        try { navigate(path); } catch (IOException failure) { failed(failure); }
    }

    /**
     * Displays an IO failure before notifying its listener; listing state is retained.
     * @param failure underlying navigation or activation failure
     */
    private void failed(IOException failure) { model.reportFailure(failure); }

    /**
     * Navigates rows and opens the selection before child buttons handle keys.
     * Parent navigation stops at the construction root; empty lists ignore row keys.
     * @param context routed preview event
     */
    @Override public void onInputPreview(UIInputEvent context) {
        if (isDisabled() || !(context.event() instanceof KeyPressEvent key)) return;
        if (key.getKey() == Keyboard.BACKSPACE) {
            if (model.canGoUp()) request(model.getDirectory().getParent()); context.consume(); return;
        }
        boolean rowTarget = false;
        for (var node = context.target(); node != null && node != this; node = node.getParent()) {
            if (node == list) { rowTarget = true; break; }
        }
        if (!rowTarget) return;
        if (model.getEntries().isEmpty()) return;
        switch (key.getKey()) {
            case Keyboard.DOWN -> select(Math.min(model.getEntries().size() - 1, model.getSelectedIndex() + 1), key.isShiftDown(), false);
            case Keyboard.UP -> select(Math.max(0, model.getSelectedIndex() - 1), key.isShiftDown(), false);
            case Keyboard.HOME -> select(0, key.isShiftDown(), false);
            case Keyboard.END -> select(model.getEntries().size() - 1, key.isShiftDown(), false);
            case Keyboard.A -> {
                if (!key.isCtrlDown() && !key.isSuperDown()) return;
                model.selectAll();
            }
            case Keyboard.ENTER -> { if (model.getSelectedIndex() < 0) return; openSelection(); }
            default -> { return; }
        }
        context.consume();
    }

    /**
     * Directory row supporting modifier selection and plain double-click activation.
     * The index refers to one immutable listing generation and is discarded on refresh.
     * @author Albert Beaupre
     */
    private final class FileRow extends Button {
        private final int index; // Position represented by this transient row.

        /**
         * Formats a file or folder name without querying the filesystem again.
         * @param index valid current listing index
         */
        private FileRow(int index) {
            super((model.getEntries().get(index).directory() ? "[Folder] " : "") + model.getEntries().get(index).path().getFileName());
            this.index = index; action(button -> select(index));
            if (details) {
                setStyleName("chooser-row"); getLabel().setVisible(false);
                ScrollPanel name = new ScrollPanel(); name.horizontal(false).vertical(false).horizontalBar(false).verticalBar(false);
                name.setClickable(false);
                name.getLayout().absolute().left(30).top(0).widthPercent(48).heightPercent(100);
                Label text = new Label(model.getEntries().get(index).path().getFileName().toString()); text.setClickable(false); text.getLayout().marginTop(6);
                name.setContent(text); add(name);
                FileIcon icon = new FileIcon(model.getEntries().get(index).directory()); icon.getLayout().absolute().left(9).top(5).width(16).height(18); add(icon);
                String date;
                try { date = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                        .format(Files.getLastModifiedTime(model.getEntries().get(index).path()).toInstant().atZone(java.time.ZoneId.systemDefault())); }
                catch (IOException | SecurityException failure) { date = "Unavailable"; }
                Label modified = new Label(date); modified.setClickable(false); modified.getLayout().absolute().leftPercent(57).top(6); add(modified);
            }
        }

        /**
         * Selects on an accepted primary release, honoring range/toggle modifiers.
         * Two plain releases on the same entry within 500 ms activate it; modifier
         * selection resets double-click tracking and never opens an entry.
         * @param event routed mouse release
         */
        @Override public void onMouseRelease(valthorne.event.events.MouseReleaseEvent event) {
            if (!isActivationRelease(event) || FileExplorer.this.isDisabled()) return;
            event.consume();
            boolean twice = model.click(index, event.isShiftDown(), event.isCtrlDown() || event.isSuperDown(), System.nanoTime());
            list.scrollToIndex(index);
            if (twice) openSelection();
        }
    }
}
