package valthorne.ui.nodes;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.ui.UIInputEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
    /**
     * Immutable directory-listing entry. Metadata represents the last successful scan
     * and can become stale before activation; activation revalidates its real path.
     * The entry owns no open stream or file handle and does not watch later changes.
     * @param path absolute entry path as encountered in the parent directory
     * @param directory whether the entry resolved to a directory during the scan
     * @author Albert Beaupre
     */
    public record Entry(Path path, boolean directory) {}

    private final Path rootDirectory; // Canonical construction-time browsing root.
    private final boolean confined; // Whether navigation must remain under the initial root.
    private final valthorne.ui.behavior.SelectionModel selection = new valthorne.ui.behavior.SelectionModel(0, false); // Index selection with optional toggle/range semantics.
    private Consumer<List<Path>> selectionChanged = paths -> {}; // Immutable selected-path snapshot notification.
    private Consumer<Path> directoryChanged = path -> {}; // Successful navigation notification after installation.
    private Path lastClicked; // Last plain-clicked entry for double-click activation.
    private long lastClickNanos; // Monotonic timestamp of the previous plain row click.
    private Path directory; // Canonical current directory after the last successful navigation.
    private List<Entry> entries = List.of(); // Immutable, sorted snapshot of visible children.
    private int selected = -1; // Selected row index, or -1 after navigation/refresh.
    private boolean showHidden; // Whether filesystem-hidden entries are included.
    private Predicate<Path> filter = path -> true; // File-only filter; directories bypass it.
    private Consumer<Path> open = path -> {}; // Application file-activation callback, never an OS launcher.
    private Consumer<IOException> error = failure -> {}; // Listener for failures caused by user navigation.
    private final BreadcrumbBar breadcrumbs = new BreadcrumbBar(); // Owned current-directory navigation.
    private final Label status = new Label(); // Owned current path, selection, or error text.
    private final Button up = new Button("Up"); // Owned parent-navigation button.
    private final VirtualList list; // Owned virtualized directory rows.
    private final Panel toolbar = new Panel(); // Optional standalone navigation commands.
    private final ScrollPanel trail = new ScrollPanel(); // Optional standalone breadcrumb viewport.
    private final ScrollPanel statusViewport = new ScrollPanel(); // Optional standalone status viewport.
    private final Panel header = new Panel(); // Detail column headings above the virtual rows.
    private boolean details; // Whether rows display file icons and metadata columns.
    private String query = ""; // Case-insensitive current-directory name filter.
    private boolean descending; // Reverse name order within the folder/file groups.
    private boolean sortModified; // Whether modification timestamps are the primary sort key.
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
        this.confined = confined;
        rootDirectory = Objects.requireNonNull(root).toRealPath();
        if (!Files.isDirectory(rootDirectory)) throw new IOException("Not a directory: " + rootDirectory);
        getLayout().column().minWidth(280).minHeight(160);
        toolbar.getLayout().row().height(36).noShrink();
        Button refresh = new Button("Refresh").action(button -> request(directory));
        Button activate = new Button("Open").action(button -> openSelection());
        up.action(button -> { if (canGoUp()) request(directory.getParent()); });
        for (Button button : List.of(up, refresh, activate)) button.getLayout().width(72).heightPercent(100).noShrink();
        toolbar.add(up, refresh, activate);
        breadcrumbs.onNavigate(this::request);
        trail.horizontal(true).vertical(false);
        trail.getLayout().height(36).noShrink(); trail.setContent(breadcrumbs);
        list = new VirtualList(0, index -> {
            Button row = new FileRow(index);
            row.setSelected(selection.isSelected(index)); return row;
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
        add(toolbar, trail, header, list, statusViewport); navigate(rootDirectory);
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
        String old = query; query = Objects.requireNonNull(text).toLowerCase(java.util.Locale.ROOT);
        try { refresh(); } catch (IOException | RuntimeException failure) { query = old; throw failure; }
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
        boolean oldModified = sortModified, oldDescending = this.descending;
        sortModified = modified; this.descending = descending;
        try { refresh(); }
        catch (IOException | RuntimeException failure) { sortModified = oldModified; this.descending = oldDescending; throw failure; }
        nameHeading.text("Name" + (modified ? "" : descending ? "  v" : "  ^"));
        dateHeading.text("Date modified" + (!modified ? "" : descending ? "  v" : "  ^")); return this;
    }

    /**
     * Toggles a column's direction or starts a newly chosen column in ascending order.
     * @param modified whether the modification-date heading was activated
     */
    private void requestSort(boolean modified) {
        try { sort(modified, sortModified == modified && !descending); }
        catch (IOException failure) { failed(failure); }
    }

    /**
     * Validates the destination's current real path against the browsing root.
     * @param path path to resolve, including any symbolic links
     * @return canonical contained path
     * @throws IOException if absent, inaccessible, or outside the root
     */
    private Path contained(Path path) throws IOException {
        Path real = Objects.requireNonNull(path).toRealPath();
        if (confined && !real.startsWith(rootDirectory)) throw new IOException("Path is outside browsing root: " + path);
        return real;
    }

    /**
     * Reads a complete candidate snapshot before any visible state changes. Sorting
     * places directories first, then case-insensitive names with exact-name tie breaks.
     * @param target canonical directory to enumerate
     * @param predicate file-only inclusion rule
     * @param hidden whether to include hidden entries
     * @return immutable sorted entries
     * @throws IOException if enumeration or hidden-state inspection fails
     */
    private List<Entry> scan(Path target, Predicate<Path> predicate, boolean hidden) throws IOException {
        if (!Files.isDirectory(target)) throw new IOException("Not a directory: " + target);
        ArrayList<Entry> next = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(target)) {
            for (Path child : stream) {
                if (!hidden && Files.isHidden(child)) continue;
                if (!child.getFileName().toString().toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
                boolean folder = Files.isDirectory(child);
                if (folder || predicate.test(child)) next.add(new Entry(child, folder));
            }
        } catch (java.nio.file.DirectoryIteratorException failure) { throw failure.getCause(); }
        Comparator<Entry> names = Comparator.comparing(entry -> entry.path().getFileName().toString(), String.CASE_INSENSITIVE_ORDER);
        names = names.thenComparing(entry -> entry.path().getFileName().toString());
        if (sortModified) {
            java.util.Map<Path, Long> dates = new java.util.HashMap<>();
            for (Entry entry : next) {
                try { dates.put(entry.path(), Files.getLastModifiedTime(entry.path()).toMillis()); }
                catch (IOException | SecurityException failure) { dates.put(entry.path(), Long.MIN_VALUE); }
            }
            names = Comparator.<Entry>comparingLong(entry -> dates.get(entry.path())).thenComparing(names);
        }
        next.sort(Comparator.comparing(Entry::directory).reversed().thenComparing(descending ? names.reversed() : names));
        return List.copyOf(next);
    }

    /**
     * Navigates atomically after a successful scan. Relative paths resolve against the
     * current directory; failures leave directory, entries, and selection unchanged.
     * @param path absolute or current-directory-relative destination
     * @return this explorer
     * @throws IOException if resolution, containment, or enumeration fails
     */
    public FileExplorer navigate(Path path) throws IOException {
        Objects.requireNonNull(path);
        Path target = contained(path.isAbsolute() || directory == null ? path : directory.resolve(path));
        List<Entry> next = scan(target, filter, showHidden);
        install(target, next); return this;
    }

    /**
     * Installs a validated snapshot and resets row selection without a file callback.
     * @param target canonical scanned directory
     * @param next sorted immutable listing
     */
    private void install(Path target, List<Entry> next) {
        Path previous = directory;
        directory = target; entries = next; selected = -1;
        selection.clear(); selection.itemCount(next.size()); lastClicked = null;
        list.itemCount(next.size()); list.refreshItems(); list.scrollY(0);
        breadcrumbs.path(confined ? rootDirectory : target.getRoot(), target); up.setEnabled(canGoUp());
        status.text(target.toString());
        selectionChanged.accept(List.of());
        if (!target.equals(previous)) directoryChanged.accept(target);
    }

    /**
     * Reports whether a parent exists and is permitted by the configured containment policy.
     * @return true when Up or Backspace can navigate to a parent
     */
    private boolean canGoUp() { return directory.getParent() != null && (!confined || !directory.equals(rootDirectory)); }

    /**
     * Configures Ctrl/Super toggles and Shift ranges; disabling collapses existing membership.
     * @param multiple whether multiple entries may be selected
     * @return this explorer
     */
    public FileExplorer multipleSelection(boolean multiple) {
        selection.multiple(multiple); synchronizeSelection(); return this;
    }

    /**
     * Copies selected paths in current listing order. Directory and file entries are
     * both selectable; a chooser applies its final selection-type policy at approval.
     * @return immutable selected-path snapshot
     */
    public List<Path> getSelectedPaths() {
        ArrayList<Path> paths = new ArrayList<>();
        selection.forEachSelected(index -> paths.add(entries.get(index).path())); return List.copyOf(paths);
    }

    /**
     * Replaces the selection callback; successful refreshes notify an empty selection.
     * @param listener nonnull synchronous listener
     * @return this explorer
     */
    public FileExplorer onSelectionChange(Consumer<List<Path>> listener) { selectionChanged = Objects.requireNonNull(listener); return this; }

    /**
     * Replaces the callback for actual successful directory changes. Filter-only refreshes
     * do not emit a directory event; the new listing is available before notification.
     * @param listener nonnull synchronous listener
     * @return this explorer
     */
    public FileExplorer onDirectoryChange(Consumer<Path> listener) { directoryChanged = Objects.requireNonNull(listener); return this; }

    /**
     * Changes the file-only filter after successfully scanning with it. Directories
     * bypass the filter; callback failures propagate without committing a partial list.
     * @param filter nonnull predicate called synchronously for each visible file
     * @return this explorer
     * @throws IOException if the new scan fails
     */
    public FileExplorer filter(Predicate<Path> filter) throws IOException {
        Objects.requireNonNull(filter);
        List<Entry> next = scan(directory, filter, showHidden);
        this.filter = filter; install(directory, next); return this;
    }

    /**
     * Rescans with a new hidden-file policy before committing it.
     * @param show whether hidden entries should be displayed
     * @return this explorer
     * @throws IOException if scanning fails
     */
    public FileExplorer showHidden(boolean show) throws IOException {
        List<Entry> next = scan(directory, filter, show);
        showHidden = show; install(directory, next); return this;
    }

    /**
     * Rescans the current directory using the active filter and hidden-file policy.
     * A successful refresh clears selection; failures preserve the previous snapshot.
     * @return this explorer
     * @throws IOException if the current directory can no longer be read or resolved
     */
    public FileExplorer refresh() throws IOException { return navigate(directory); }

    /**
     * Reads the canonical current directory; no filesystem query occurs.
     * @return current absolute directory
     */
    public Path getDirectory() { return directory; }

    /**
     * Reads the immutable last successful directory snapshot.
     * @return entries in displayed order
     */
    public List<Entry> getEntries() { return entries; }

    /**
     * Reads the selected path from the snapshot without resolving it again.
     * @return selected path or null when nothing is selected
     */
    public Path getSelected() { return selected < 0 || !selection.isSelected(selected) ? null : entries.get(selected).path(); }

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
        if (index < -1 || index >= entries.size()) throw new IndexOutOfBoundsException(index);
        selected = index;
        if (index < 0) selection.clear(); else selection.select(index, extend, toggle);
        synchronizeSelection();
        if (index >= 0) list.scrollToIndex(index);
    }

    /**
     * Updates live row highlights and status before emitting an immutable path snapshot.
     * Virtualized rows created later read the same retained selection model.
     */
    private void synchronizeSelection() {
        selected = selection.lead();
        for (int i = 0; i < entries.size(); i++) {
            var row = list.getItemNode(i); if (row != null) row.setSelected(selection.isSelected(i));
        }
        status.text(selected < 0 ? directory.toString() : entries.get(selected).path().toString());
        selectionChanged.accept(getSelectedPaths());
    }

    /**
     * Revalidates selection, entering a directory or notifying the application for an
     * existing file. IO failures update status and notify onError; callback exceptions
     * propagate. No native application is launched and no filesystem data is changed.
     */
    public void openSelection() {
        if (isDisabled() || getSelected() == null) return;
        Path target;
        try {
            target = contained(entries.get(selected).path());
            if (Files.isDirectory(target)) { navigate(target); return; }
        } catch (IOException failure) { failed(failure); return; }
        open.accept(target);
    }

    /**
     * Replaces the callback for file activation; directory navigation does not emit it.
     * @param listener nonnull callback receiving the current canonical file path
     * @return this explorer
     */
    public FileExplorer onOpen(Consumer<Path> listener) { open = Objects.requireNonNull(listener); return this; }

    /**
     * Replaces the listener for user-triggered IO failures. Direct navigate/filter calls
     * throw instead, allowing application code to choose its own error handling.
     * @param listener nonnull synchronous failure callback
     * @return this explorer
     */
    public FileExplorer onError(Consumer<IOException> listener) { error = Objects.requireNonNull(listener); return this; }

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
    private void failed(IOException failure) { status.text(failure.getMessage()); error.accept(failure); }

    /**
     * Navigates rows and opens the selection before child buttons handle keys.
     * Parent navigation stops at the construction root; empty lists ignore row keys.
     * @param context routed preview event
     */
    @Override public void onInputPreview(UIInputEvent context) {
        if (isDisabled() || !(context.event() instanceof KeyPressEvent key)) return;
        if (key.getKey() == Keyboard.BACKSPACE) {
            if (canGoUp()) request(directory.getParent()); context.consume(); return;
        }
        boolean rowTarget = false;
        for (var node = context.target(); node != null && node != this; node = node.getParent()) {
            if (node == list) { rowTarget = true; break; }
        }
        if (!rowTarget) return;
        if (entries.isEmpty()) return;
        switch (key.getKey()) {
            case Keyboard.DOWN -> select(Math.min(entries.size() - 1, selected + 1), key.isShiftDown(), false);
            case Keyboard.UP -> select(Math.max(0, selected - 1), key.isShiftDown(), false);
            case Keyboard.HOME -> select(0, key.isShiftDown(), false);
            case Keyboard.END -> select(entries.size() - 1, key.isShiftDown(), false);
            case Keyboard.A -> {
                if (!key.isCtrlDown() && !key.isSuperDown()) return;
                selection.selectAll(); synchronizeSelection();
            }
            case Keyboard.ENTER -> { if (selected < 0) return; openSelection(); }
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
            super((entries.get(index).directory() ? "[Folder] " : "") + entries.get(index).path().getFileName());
            this.index = index; action(button -> select(index));
            if (details) {
                setStyleName("chooser-row"); getLabel().setVisible(false);
                ScrollPanel name = new ScrollPanel(); name.horizontal(false).vertical(false).horizontalBar(false).verticalBar(false);
                name.setClickable(false);
                name.getLayout().absolute().left(30).top(0).widthPercent(48).heightPercent(100);
                Label text = new Label(entries.get(index).path().getFileName().toString()); text.setClickable(false); text.getLayout().marginTop(6);
                name.setContent(text); add(name);
                FileIcon icon = new FileIcon(entries.get(index).directory()); icon.getLayout().absolute().left(9).top(5).width(16).height(18); add(icon);
                String date;
                try { date = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                        .format(Files.getLastModifiedTime(entries.get(index).path()).toInstant().atZone(java.time.ZoneId.systemDefault())); }
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
            boolean modified = event.isCtrlDown() || event.isSuperDown() || event.isShiftDown();
            Path path = entries.get(index).path(); long now = System.nanoTime();
            boolean twice = !modified && path.equals(lastClicked) && now - lastClickNanos < 500_000_000L;
            select(index, event.isShiftDown(), event.isCtrlDown() || event.isSuperDown());
            lastClicked = modified || twice ? null : path; lastClickNanos = now;
            if (twice) openSelection();
        }
    }
}
