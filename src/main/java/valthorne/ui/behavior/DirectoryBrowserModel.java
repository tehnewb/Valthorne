package valthorne.ui.behavior;

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
 * Renderer-independent directory navigation, filtering, ordering and selection.
 * Scans are synchronous and commit a complete snapshot only after success. Relative
 * navigation resolves against the current directory; containment uses real paths.
 * Use on the UI thread. Listeners run synchronously after state and view updates;
 * listener exceptions propagate and do not roll back a committed snapshot.
 */
public final class DirectoryBrowserModel {
    /** Immutable listing entry; metadata is a snapshot and activation revalidates the path. */
    public record Entry(Path path, boolean directory) {}

    private final Path rootDirectory; // Canonical construction-time browsing root.
    private final boolean confined; // Whether navigation must remain under the initial root.
    private final SelectionModel selection = new SelectionModel(0, false); // Index selection with optional toggle/range semantics.
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
    private String query = ""; // Case-insensitive current-directory name filter.
    private boolean descending; // Reverse name order within the folder/file groups.
    private boolean sortModified; // Whether modification timestamps are the primary sort key.
    private Runnable listingChanged = () -> {};
    private Runnable selectionUpdated = () -> {};
    private Consumer<IOException> failureDisplayed = failure -> {};

    /** Resolves the browsing root and takes the initial directory snapshot. */
    public DirectoryBrowserModel(Path root, boolean confined) throws IOException {
        this.confined = confined;
        rootDirectory = Objects.requireNonNull(root).toRealPath();
        navigate(rootDirectory);
    }

    /** Installs view synchronization hooks, which run before application callbacks. */
    public void bindView(Runnable listing, Runnable selected, Consumer<IOException> failed) {
        listingChanged = Objects.requireNonNull(listing);
        selectionUpdated = Objects.requireNonNull(selected);
        failureDisplayed = Objects.requireNonNull(failed);
    }

    /** Returns the first path component permitted in the breadcrumb view. */
    public Path getBreadcrumbRoot() { return confined ? rootDirectory : directory.getRoot(); }
    /** Tests membership in the current listing selection. */
    public boolean isSelected(int index) { return selection.isSelected(index); }
    /** Returns the lead row, or -1 for an empty selection. */
    public int getSelectedIndex() { return selected; }
    /** Reports whether modification time is the primary ordering key. */
    public boolean isSortModified() { return sortModified; }
    /** Reports reverse ordering within each folder/file group. */
    public boolean isDescending() { return descending; }

    /** Changes membership without imposing widget focus, scrolling or enabled state. */
    public void select(int index, boolean extend, boolean toggle) {
        if (index < -1 || index >= entries.size()) throw new IndexOutOfBoundsException(index);
        if (index < 0) selection.clear(); else selection.select(index, extend, toggle);
        synchronizeSelection();
    }

    /** Selects all rows when multiple selection is enabled. */
    public void selectAll() { selection.selectAll(); synchronizeSelection(); }

    /** Tracks a primary release; returns whether it completes a plain double-click. */
    public boolean click(int index, boolean extend, boolean toggle, long now) {
        Path path = entries.get(index).path();
        boolean modified = extend || toggle;
        boolean twice = !modified && path.equals(lastClicked) && now - lastClickNanos < 500_000_000L;
        select(index, extend, toggle);
        lastClicked = modified || twice ? null : path;
        lastClickNanos = now;
        return twice;
    }

    /** Applies a case-insensitive name query after a successful rescan. */
    public DirectoryBrowserModel search(String text) throws IOException {
        String old = query; query = Objects.requireNonNull(text).toLowerCase(java.util.Locale.ROOT);
        Path target;
        List<Entry> next;
        try {
            target = contained(directory);
            next = scan(target, filter, showHidden);
        } catch (IOException | RuntimeException failure) { query = old; throw failure; }
        install(target, next);
        return this;
    }

    /** Applies ordering after a successful rescan; folders always precede files. */
    public DirectoryBrowserModel sort(boolean modified, boolean descending) throws IOException {
        boolean oldModified = sortModified, oldDescending = this.descending;
        sortModified = modified; this.descending = descending;
        Path target;
        List<Entry> next;
        try {
            target = contained(directory);
            next = scan(target, filter, showHidden);
        } catch (IOException | RuntimeException failure) {
            sortModified = oldModified; this.descending = oldDescending; throw failure;
        }
        install(target, next);
        return this;
    }

    private Path contained(Path path) throws IOException {
        Path real = Objects.requireNonNull(path).toRealPath();
        if (confined && !real.startsWith(rootDirectory)) throw new IOException("Path is outside browsing root: " + path);
        return real;
    }

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

    /** Resolves and scans a destination before replacing the listing and clearing selection. */
    public DirectoryBrowserModel navigate(Path path) throws IOException {
        Objects.requireNonNull(path);
        Path target = contained(path.isAbsolute() || directory == null ? path : directory.resolve(path));
        List<Entry> next = scan(target, filter, showHidden);
        install(target, next); return this;
    }

    private void install(Path target, List<Entry> next) {
        Path previous = directory;
        directory = target; entries = next; selected = -1;
        selection.clear(); selection.itemCount(next.size()); lastClicked = null;
        listingChanged.run();
        selectionChanged.accept(List.of());
        if (!target.equals(previous)) directoryChanged.accept(target);
    }

    /** Reports whether parent navigation is permitted by the browsing policy. */
    public boolean canGoUp() { return directory.getParent() != null && (!confined || !directory.equals(rootDirectory)); }

    /** Enables range/toggle selection, or collapses membership to a single row. */
    public DirectoryBrowserModel multipleSelection(boolean multiple) {
        selection.multiple(multiple); synchronizeSelection(); return this;
    }

    /** Returns an immutable selection snapshot in listing order. */
    public List<Path> getSelectedPaths() {
        ArrayList<Path> paths = new ArrayList<>();
        selection.forEachSelected(index -> paths.add(entries.get(index).path())); return List.copyOf(paths);
    }

    /** Replaces the application listener called after view selection updates. */
    public DirectoryBrowserModel onSelectionChange(Consumer<List<Path>> listener) { selectionChanged = Objects.requireNonNull(listener); return this; }

    /** Replaces the listener for actual directory changes, excluding filter-only refreshes. */
    public DirectoryBrowserModel onDirectoryChange(Consumer<Path> listener) { directoryChanged = Objects.requireNonNull(listener); return this; }

    /** Applies a file predicate after a successful scan; folders bypass the predicate. */
    public DirectoryBrowserModel filter(Predicate<Path> filter) throws IOException {
        Objects.requireNonNull(filter);
        List<Entry> next = scan(directory, filter, showHidden);
        this.filter = filter; install(directory, next); return this;
    }

    /** Commits the hidden-entry policy only after scanning succeeds. */
    public DirectoryBrowserModel showHidden(boolean show) throws IOException {
        List<Entry> next = scan(directory, filter, show);
        showHidden = show; install(directory, next); return this;
    }

    /** Rescans the current directory and clears selection on success. */
    public DirectoryBrowserModel refresh() throws IOException { return navigate(directory); }

    /** Returns the canonical current directory without querying the filesystem. */
    public Path getDirectory() { return directory; }

    /** Returns the immutable snapshot from the last successful scan. */
    public List<Entry> getEntries() { return entries; }

    /** Returns the selected lead path, or null when the lead is not selected. */
    public Path getSelected() { return selected < 0 || !selection.isSelected(selected) ? null : entries.get(selected).path(); }

    private void synchronizeSelection() {
        selected = selection.lead();
        selectionUpdated.run();
        selectionChanged.accept(getSelectedPaths());
    }

    /** Revalidates the lead path, then navigates or emits a file callback; never launches an application. */
    public void openSelection() {
        if (getSelected() == null) return;
        Path target;
        try {
            target = contained(entries.get(selected).path());
            if (Files.isDirectory(target)) { navigate(target); return; }
        } catch (IOException failure) { reportFailure(failure); return; }
        open.accept(target);
    }

    /** Replaces the application callback for activation of a canonical file path. */
    public DirectoryBrowserModel onOpen(Consumer<Path> listener) { open = Objects.requireNonNull(listener); return this; }

    /** Replaces the listener for user navigation and activation failures. */
    public DirectoryBrowserModel onError(Consumer<IOException> listener) { error = Objects.requireNonNull(listener); return this; }

    /** Updates the view's error display before notifying the application. */
    public void reportFailure(IOException failure) { failureDisplayed.accept(failure); error.accept(failure); }
}
