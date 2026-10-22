package valthorne.ui.nodes.nano;

import org.joml.Vector2f;
import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.FileDropEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.graphics.Color;
import valthorne.ui.UIInputEvent;
import valthorne.ui.UINode;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;
import valthorne.ui.theme.UITokens;
import valthorne.viewport.Viewport;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Compact filesystem hierarchy with flat full-width rows, vector disclosure
 * arrows and folder icons, smooth interface text, and independent navigation
 * and context-menu selection. Roots are application-supplied places or drives;
 * opening a branch lazily snapshots its immediate nonhidden directories.
 * Symbolic links are excluded to prevent expansion cycles.
 *
 * <p>Rendering, layout, and keyboard navigation use cached paths only. The
 * virtual list materializes the viewport rather than the entire tree. Reopening
 * a collapsed branch reuses its snapshot; call {@link #refresh()} after an
 * external filesystem change. Scans commit only after success and notify the
 * error listener without replacing prior rows.</p>
 *
 * <p>Clicking a row requests navigation; double-clicking expands that directory
 * without collapsing an already expanded branch. The owner commits a successful visit
 * with {@link #reveal(Path)}. Right-clicking highlights the command target and
 * opens the file menu without changing the last visited directory. Arrow keys
 * move a stable tree focus, Enter opens the focused directory, and Menu or
 * Shift+F10 opens its context menu. The default menu supports file commands;
 * application roots are protected against renaming, cutting, and deletion.
 * Desktop drops copy paths into the hovered directory row or the visited folder
 * over empty space. Imports run asynchronously, preserve colliding destination
 * names, and refresh companion listings through the navigation callback.
 * Default file commands rescan the owner's visited directory through its
 * navigation callback; removing that directory recovers to an existing parent.
 * An owner can replace this menu with {@link #onContextMenu(BiConsumer)}.</p>
 *
 * <pre>{@code
 * NanoDirectoryTree tree = new NanoDirectoryTree(List.of(projectDirectory));
 * tree.onNavigate(path -> {
 *     browser.navigate(path);
 *     tree.reveal(browser.getDirectory());
 * });
 * tree.reveal(browser.getDirectory());
 * }</pre>
 *
 * All operations are confined to the UI thread. Font and theme resources are
 * borrowed from the root; no texture or native font resource is owned here.
 * This is an explicit snapshot, not a filesystem watcher.
 *
 * @author Albert Beaupre
 */
public class NanoDirectoryTree extends NanoPanel {
    /**
     * Maximum interval between row clicks that expands a directory.
     */
    private static final long DOUBLE_CLICK_NANOS = 500_000_000L;

    /**
     * Maximum squared pointer distance between clicks, in window coordinate units.
     */
    private static final float DOUBLE_CLICK_DISTANCE_SQUARED = 25f;

    /**
     * Height of a compact directory row in logical UI units.
     */
    public static final StyleKey<Float> ROW_HEIGHT_KEY = StyleKey.of("nano.directoryTree.rowHeight", Float.class, 24f);
    /**
     * Horizontal advance between hierarchy levels in logical UI units.
     */
    public static final StyleKey<Float> INDENT_KEY = StyleKey.of("nano.directoryTree.indent", Float.class, 16f);
    /**
     * Logical em size shared by every directory caption.
     */
    public static final StyleKey<Float> FONT_SIZE_KEY = StyleKey.of("nano.directoryTree.fontSize", Float.class, 14f);
    /**
     * Warm muted folder tint used when no explicit theme override is present.
     */
    public static final StyleKey<Color> FOLDER_COLOR_KEY = StyleKey.of("nano.directoryTree.folderColor", Color.class, new Color(0xFFC5A066));
    /**
     * Default readable foreground for a tree without semantic theme colors.
     */
    private static final Color DEFAULT_TEXT = new Color(0xFFDEDEDE);
    /**
     * Default subdued disclosure foreground outside hover and selection.
     */
    private static final Color DEFAULT_MUTED = new Color(0xFF989898);
    /**
     * Unavailable directory artwork and captions, visibly dimmer than ordinary muted ink.
     */
    private static final Color DEFAULT_DISABLED = new Color(0xFF5A5A5A);
    /**
     * Default pointer-hover surface without a selected directory.
     */
    private static final Color DEFAULT_HOVER = new Color(0xFF303030);
    /**
     * Default blue-gray selection surface, matching editor content lists.
     */
    private static final Color DEFAULT_SELECTION = new Color(0xFF405A70);
    /**
     * Default restrained keyboard-focus marker at the leading row edge.
     */
    private static final Color DEFAULT_ACCENT = new Color(0xFF77A9CE);
    /**
     * Case-insensitive ordering used only while creating directory snapshots.
     */
    private static final Comparator<Path> DIRECTORY_ORDER = Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER);

    private final List<Path> roots; // Immutable distinct normalized application places.
    private final Map<Path, List<Path>> children = new HashMap<>(); // Cached immediate directories by scanned branch.
    private final Set<Path> expanded = new HashSet<>(); // Expanded branches retained across collapse and navigation.
    private final NanoVirtualList list; // Owned bounded viewport for the depth-first snapshot.
    private final NanoFileContextMenu fileMenu; // Owned default file command menu, replaceable through the context callback.
    private List<NanoDirectoryBranch> visible = List.of(); // Immutable current flattened generation.
    private Path selected; // Last successfully visited directory, or null before the first reveal.
    private Path contextPath; // Highlighted command target, or null before row interaction.
    private Path focusedPath; // Stable keyboard target independent of recycled row nodes.
    private float rowHeight = 24f; // Resolved row height in logical units.
    private float indent = 16f; // Resolved hierarchy indentation in logical units.
    private float fontSize = 14f; // Resolved interface caption size in logical units.
    private boolean customContextMenu; // Whether file shortcuts belong to an enclosing owner's menu controller.
    private Consumer<Path> navigate = path -> {}; // Borrowed synchronous directory activation callback.
    private Consumer<IOException> error = failure -> {}; // Borrowed scan-failure callback preserving the old snapshot.
    private BiConsumer<Path, MousePressEvent> contextMenu; // Owner callback for pointer and keyboard context requests.
    private String rootCaption; // Optional application alias for root rows; null preserves filesystem names.
    private BiConsumer<List<Path>, Path> imported; // Optional owner import handler; null uses the tree's default file controller.
    private Path lastClickedPath; // Previous row click, retained independently of recycled virtual rows.
    private long lastClickNanos; // Monotonic timestamp of the previous accepted row click.
    private float lastClickX; // Previous row-click horizontal window coordinate.
    private float lastClickY; // Previous row-click vertical window coordinate.

    /**
     * Creates a borderless hierarchy without eagerly reading directory contents.
     *
     * @param roots ordered nonnull directory places, such as a project, Home, or drives
     * @throws NullPointerException if roots or any root is null
     */
    public NanoDirectoryTree(List<Path> roots) {
        Objects.requireNonNull(roots);
        ArrayList<Path> places = new ArrayList<>(roots.size());
        for (Path root : roots) {
            Path normalized = Objects.requireNonNull(root)
                    .toAbsolutePath()
                    .normalize();
            if (!places.contains(normalized)) places.add(normalized);
        }
        this.roots = List.copyOf(places);
        getLayout()
                .column()
                .minWidth(0)
                .minHeight(0);
        setStyleName("chooser-pane");
        setFocusable(true);
        list = new NanoVirtualList(0, this::row)
                .rowHeight(rowHeight)
                .gap(0);
        list.setStyleName("widget-layout");
        list.getContent()
                .setStyleName("widget-layout");
        list.getLayout()
                .height(0)
                .grow(1)
                .minHeight(0)
                .widthPercent(100);
        fileMenu = new NanoFileContextMenu(this, this::contextSelection, this::contextDestination)
                .protectedPaths(this.roots)
                .onChanged(this::filesChanged)
                .onOpen(this::activateRow)
                .onError(failure -> error.accept(failure));
        contextMenu = (path, event) -> fileMenu.showFor(this, path == null ? List.of() : List.of(path), path == null ? contextDestination() : path, event);
        add(list);
        rebuild();
    }

    /**
     * Creates a row holding a stable path snapshot rather than a mutable index.
     *
     * @param index position in the current visible generation
     * @return newly owned virtual row
     */
    private NanoDirectoryRow row(int index) {
        return new NanoDirectoryRow(this, visible.get(index));
    }

    /**
     * Assigns a display alias to root rows without changing their filesystem paths.
     * Passing null restores ordinary directory names. Existing virtual rows are
     * recreated so captions and ellipsis caches remain consistent.
     *
     * @param caption root label, or null for actual names
     * @return this hierarchy
     */
    public NanoDirectoryTree rootCaption(String caption) {
        rootCaption = caption;
        list.refreshItems();
        return this;
    }

    /**
     * Replaces desktop-file importing while retaining row destination picking.
     * Standalone trees otherwise use their own checked asynchronous file commands.
     *
     * @param listener nonnull source-snapshot and destination callback
     * @return this hierarchy
     */
    public NanoDirectoryTree onFileImport(BiConsumer<List<Path>, Path> listener) {
        imported = Objects.requireNonNull(listener);
        return this;
    }

    @Override
    public void onFileDrop(FileDropEvent event) {
        if (isDisabled() || event.getPaths().isEmpty() || getRoot() == null) return;
        Path destination = selected != null ? selected : roots.size() == 1 ? roots.getFirst() : null;
        for (UINode node = getRoot().findNodeAt(event.getX(), event.getY(), CLICKABLE_BIT); node != null && node != this; node = node.getParent())
            if (node instanceof NanoDirectoryRow row) {
                destination = row.getPath();
                break;
        }
        if (destination == null) return;
        event.consume();
        if (imported != null) imported.accept(event.getPaths(), destination);
        else fileMenu.importFiles(event.getPaths(), destination);
    }

    /**
     * Resolves one cached row caption without filesystem access.
     *
     * @param path normalized directory represented by the row
     * @return root alias or actual directory caption
     */
    String caption(Path path) {
        if (rootCaption != null && roots.contains(path)) return rootCaption;
        return path.getFileName() == null ? "This PC (" + path + ")" : path.getFileName().toString();
    }

    /**
     * Opens a branch from its cache, scanning only its first expansion or after
     * explicit refresh. A failed scan leaves expansion and visible rows intact.
     *
     * @param path directory to expand
     * @return whether the branch was successfully opened
     * @throws NullPointerException if path is null
     */
    public boolean expand(Path path) {
        if (isDisabled()) return false;
        Path normalized = Objects.requireNonNull(path)
                .toAbsolutePath()
                .normalize();
        if (expanded.contains(normalized)) return true;
        try {
            Map<Path, List<Path>> pending = new HashMap<>();
            loadExpandedBranch(normalized, pending);
            children.putAll(pending);
            expanded.add(normalized);
            rebuild();
            return true;
        } catch (IOException | SecurityException failure) {
            error.accept(failure instanceof IOException io ? io : new IOException("Cannot read " + normalized, failure));
            return false;
        }
    }

    /**
     * Collapses a branch while retaining its cached children and descendant state.
     *
     * @param path directory to collapse
     * @throws NullPointerException if path is null
     */
    public void collapse(Path path) {
        if (isDisabled()) return;
        if (expanded.remove(Objects.requireNonNull(path).toAbsolutePath().normalize())) rebuild();
    }

    /**
     * Commits a successfully visited directory and exposes its ancestors beneath
     * the closest matching root. This does not request navigation back to the
     * owner. Successful owner navigation resets any temporary context highlight.
     *
     * @param path visited directory
     * @throws NullPointerException if path is null
     */
    public void reveal(Path path) {
        selected = Objects.requireNonNull(path).toAbsolutePath().normalize();
        contextPath = selected;
        focusedPath = selected;
        Path best = null;
        for (Path root : roots)
            if (selected.startsWith(root) && (best == null || root.getNameCount() > best.getNameCount())) best = root;
        if (best != null) {
            Path cursor = best;
            for (Path part : best.relativize(selected)) {
                if (part.toString().isEmpty()) break;
                if (!expanded.contains(cursor) && !expand(cursor)) break;
                Path child = cursor.resolve(part);
                List<Path> siblings = children.getOrDefault(cursor, List.of());
                if (!siblings.contains(child)) {
                    ArrayList<Path> updated = new ArrayList<>(siblings.size() + 1);
                    updated.addAll(siblings);
                    updated.add(child);
                    updated.sort(DIRECTORY_ORDER);
                    children.put(cursor, List.copyOf(updated));
                }
                cursor = child;
            }
        }
        rebuild();
        revealRow(selected);
    }

    /**
     * Reloads reachable expanded branches and reports failures through the error
     * listener. Collapsed caches are discarded so their next opening sees changes.
     * Selection, expansion, scroll position, and stable tree focus are preserved.
     */
    public void refresh() {
        try {
            refreshChecked();
        } catch (IOException failure) {
            error.accept(failure);
        }
    }

    /**
     * Refreshes default-menu mutations and resynchronizes the owner's listing.
     * If the visited directory was moved, renamed, or deleted, navigation recovers
     * to its nearest existing ancestor before requesting the owner's rescan.
     * Same-directory navigation deliberately refreshes owner listings as well.
     * A surviving command target retains its highlight without stealing UI focus.
     */
    private void filesChanged() {
        Path highlighted = contextPath;
        refresh();
        try {
            if (selected == null) return;
            Path directory = selected;
            while (directory != null && !Files.isDirectory(directory)) directory = directory.getParent();
            if (directory == null) throw new IOException("The visited directory is no longer available.");
            reveal(directory);
            navigate.accept(directory);
            if (highlighted == null) contextPath = null;
            else if (indexOf(highlighted) >= 0) {
                contextPath = highlighted;
                focusedPath = highlighted;
            }
        } catch (IOException | SecurityException failure) {
            error.accept(failure instanceof IOException io ? io : new IOException("Cannot refresh directory tree", failure));
        }
    }

    /**
     * Transactionally reloads reachable expanded directories after file commands.
     * No partial scan is published if an existing branch cannot be read. Removed
     * descendants naturally disappear from the new snapshot.
     *
     * @throws IOException if any reachable expanded branch cannot be enumerated
     */
    public void refreshChecked() throws IOException {
        Map<Path, List<Path>> refreshed = new HashMap<>();
        try {
            for (Path root : roots) refreshBranch(root, refreshed);
        } catch (SecurityException failure) {
            throw new IOException("Cannot refresh directory tree", failure);
        }
        children.clear();
        children.putAll(refreshed);
        rebuild();
        if (indexOf(contextPath) < 0) contextPath = selected;
        if (indexOf(focusedPath) < 0) focusedPath = indexOf(selected) >= 0 ? selected : roots.isEmpty() ? null : roots.getFirst();
    }

    /**
     * Reads only branches reachable through freshly enumerated expanded parents.
     *
     * @param path current root or known child directory
     * @param refreshed transactional destination cache
     * @throws IOException if the branch cannot be scanned
     */
    private void refreshBranch(Path path, Map<Path, List<Path>> refreshed) throws IOException {
        if (!expanded.contains(path)) return;
        List<Path> found = scan(path);
        refreshed.put(path, found);
        for (Path child : found) refreshBranch(child, refreshed);
    }

    /**
     * Loads missing snapshots for a newly opened branch and any previously
     * expanded descendants. Refresh discards collapsed caches, so reopening an
     * ancestor must reload those descendants instead of displaying empty branches.
     * Existing snapshots are reused; new scans remain transactional until commit.
     *
     * @param path branch to expose
     * @param pending successfully scanned snapshots awaiting publication
     * @throws IOException if a missing visible snapshot cannot be read
     */
    private void loadExpandedBranch(Path path, Map<Path, List<Path>> pending) throws IOException {
        List<Path> found = children.get(path);
        if (found == null) {
            found = scan(path);
            pending.put(path, found);
        }
        for (Path child : found)
            if (expanded.contains(child)) loadExpandedBranch(child, pending);
    }

    /**
     * Enumerates one directory without following symbolic links or revealing
     * hidden children. Sorting and immutable allocation occur only during scans.
     *
     * @param path normalized directory
     * @return immutable alphabetically ordered child paths
     * @throws IOException if enumeration fails
     */
    private List<Path> scan(Path path) throws IOException {
        ArrayList<Path> found = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path child : stream)
                if (!Files.isSymbolicLink(child) && Files.isDirectory(child) && !Files.isHidden(child)) found.add(child);
        } catch (DirectoryIteratorException failure) {
            throw failure.getCause();
        }
        found.sort(DIRECTORY_ORDER);
        return List.copyOf(found);
    }

    /**
     * Replaces the flattened generation and invalidates only virtual row nodes.
     * Tree focus stays on this stable container across materialization changes.
     */
    private void rebuild() {
        ArrayList<NanoDirectoryBranch> rows = new ArrayList<>();
        for (Path root : roots) append(rows, root, 0);
        visible = List.copyOf(rows);
        list.itemCount(visible.size());
    }

    /**
     * Traverses cached children without making filesystem calls during layout.
     *
     * @param rows destination depth-first snapshot
     * @param path current directory
     * @param depth indentation level relative to the application root
     */
    private void append(List<NanoDirectoryBranch> rows, Path path, int depth) {
        rows.add(new NanoDirectoryBranch(path, depth));
        if (expanded.contains(path))
            for (Path child : children.getOrDefault(path, List.of())) append(rows, child, depth + 1);
    }

    /**
     * Returns the flattened snapshot without transferring tree ownership.
     *
     * @return immutable depth-first visible branches
     */
    public List<NanoDirectoryBranch> getVisibleBranches() {
        return visible;
    }

    /**
     * Reads the last successfully visited directory independently of context
     * selection. Expansion, keyboard focus, and right-click do not change it.
     *
     * @return visited directory, or null before the first reveal
     */
    public Path getSelected() {
        return selected;
    }

    /**
     * Reads the highlighted command target without requesting navigation.
     *
     * @return latest context or focused row target, or null after an empty-area menu request
     */
    public Path getContextPath() {
        return contextPath;
    }

    /**
     * Exposes the owned viewport for scrolling and accessibility integration.
     *
     * @return virtual row list owned by this tree
     */
    public NanoVirtualList getList() {
        return list;
    }

    /**
     * Replaces the visit and rescan callback. Row activation requests a directory
     * visit; default file-menu changes also request the current or recovered
     * directory so an owner's listing refreshes even without a path change.
     * Commit successful owner navigation with reveal. Callback failures propagate
     * synchronously without claiming successful owner navigation.
     *
     * @param listener nonnull directory activation consumer
     * @return this tree
     */
    public NanoDirectoryTree onNavigate(Consumer<Path> listener) {
        navigate = Objects.requireNonNull(listener);
        return this;
    }

    /**
     * Replaces the scan-failure listener; failures preserve the current snapshot.
     *
     * @param listener nonnull failure consumer
     * @return this tree
     */
    public NanoDirectoryTree onError(Consumer<IOException> listener) {
        error = Objects.requireNonNull(listener);
        return this;
    }

    /**
     * Replaces the default file menu and hands file shortcut routing to the owner.
     * Pointer events are borrowed only for the callback. Keyboard requests supply
     * a coordinate-only right-button event beneath the focused row; this event is
     * never published into the input system. The owner owns the replacement menu
     * lifetime and all requested filesystem operations.
     *
     * @param listener nonnull directory and screen-coordinate callback; the path is null over empty space
     * @return this tree
     */
    public NanoDirectoryTree onContextMenu(BiConsumer<Path, MousePressEvent> listener) {
        contextMenu = Objects.requireNonNull(listener);
        customContextMenu = true;
        fileMenu.hide();
        return this;
    }

    /**
     * Creates the default file controller's selection only when requested.
     *
     * @return highlighted directory, or an empty list before any selection
     */
    private List<Path> contextSelection() {
        return contextPath == null ? List.of() : List.of(contextPath);
    }

    /**
     * Reads the default creation and paste destination without filesystem I/O.
     *
     * @return highlighted, visited, or first root directory; null for an empty tree
     */
    private Path contextDestination() {
        return contextPath != null ? contextPath : selected != null ? selected : roots.isEmpty() ? null : roots.getFirst();
    }

    /**
     * Records a stable row target and moves focus off recyclable virtual rows.
     *
     * @param path immutable row directory
     */
    void focusRow(Path path) {
        focusedPath = path;
        contextPath = path;
        if (getRoot() != null) getRoot()
                .setFocusTo(this);
    }

    /**
     * Requests directory navigation from a stable row path snapshot.
     *
     * @param path directory represented by the activated row
     */
    void activateRow(Path path) {
        focusRow(path);
        navigate.accept(path);
    }

    /**
     * Navigates on a single click and expands on a nearby second click. Tracking
     * belongs to the tree because navigation may recreate the clicked virtual row.
     * An expanded directory remains open, and the second click does not emit a
     * duplicate navigation request.
     *
     * @param path stable directory represented by the clicked row
     * @param event accepted primary-button release with window coordinates
     */
    void clickRow(Path path, MouseReleaseEvent event) {
        long now = System.nanoTime();
        float dx = event.getX() - lastClickX;
        float dy = event.getY() - lastClickY;
        boolean twice = path.equals(lastClickedPath) && now - lastClickNanos <= DOUBLE_CLICK_NANOS && dx * dx + dy * dy <= DOUBLE_CLICK_DISTANCE_SQUARED;
        lastClickedPath = twice ? null : path;
        lastClickNanos = now;
        lastClickX = event.getX();
        lastClickY = event.getY();
        if (twice) {
            focusRow(path);
            expand(path);
        } else activateRow(path);
    }

    /**
     * Toggles a disclosure independently of directory activation.
     *
     * @param path directory represented by the disclosure hit target
     */
    void toggleRow(Path path) {
        lastClickedPath = null;
        focusRow(path);
        if (expanded.contains(path)) collapse(path);
        else expand(path);
    }

    /**
     * Determines the display selection without coupling it to navigation.
     *
     * @param path row directory
     * @return whether this row is highlighted as the current command target
     */
    boolean isRowSelected(Path path) {
        return path.equals(contextPath);
    }

    /**
     * Reads expansion from the cached hierarchy state.
     *
     * @param path row directory
     * @return whether cached children are currently visible
     */
    boolean isExpanded(Path path) {
        return expanded.contains(path);
    }

    /**
     * Hides disclosure triangles for a scanned branch known to be empty.
     * Unknown branches remain expandable without a hot-path filesystem query.
     *
     * @param path row directory
     * @return whether disclosure is meaningful or the children are unknown
     */
    boolean canExpand(Path path) {
        List<Path> known = children.get(path);
        return known == null || !known.isEmpty();
    }

    /**
     * Reads the configured hierarchy indentation.
     *
     * @return horizontal advance per depth in UI units
     */
    float getIndent() {
        return indent;
    }

    /**
     * Reads the shared resolved interface caption size.
     *
     * @return logical em size
     */
    float getFontSize() {
        return fontSize;
    }

    /**
     * Resolves a semantic foreground with a detached-tree fallback.
     *
     * @return borrowed text tint
     */
    Color textColor() {
        return disabledInHierarchy() ? DEFAULT_DISABLED : color(UITokens.TEXT, DEFAULT_TEXT);
    }

    /**
     * Resolves a restrained disclosure tint appropriate to its row state.
     *
     * @param emphasized whether the row or disclosure is active
     * @return borrowed foreground tint
     */
    Color disclosureColor(boolean emphasized) {
        if (disabledInHierarchy()) return DEFAULT_DISABLED;
        return emphasized ? textColor() : color(UITokens.MUTED, DEFAULT_MUTED);
    }

    /**
     * Resolves a full-row selected or pointer-hover surface.
     *
     * @param selected whether this is the highlighted command target
     * @return borrowed row surface tint
     */
    Color rowColor(boolean selected) {
        return selected ? color(UITokens.SELECTION, DEFAULT_SELECTION) : color(UITokens.HOVER, DEFAULT_HOVER);
    }

    /**
     * Resolves the folder face without loading an icon or texture resource.
     *
     * @return borrowed folder tint
     */
    Color folderColor() {
        return disabledInHierarchy() ? DEFAULT_DISABLED : color(FOLDER_COLOR_KEY, FOLDER_COLOR_KEY.getDefaultValue());
    }

    /**
     * Checks owner availability for decorative directory icons and captions.
     *
     * @return whether this hierarchy or an ancestor is disabled
     */
    private boolean disabledInHierarchy() {
        for (UINode node = this; node != null; node = node.getParent())
            if (node.isDisabled()) return true;
        return false;
    }

    /**
     * Resolves the narrow keyboard-focus accent.
     *
     * @return borrowed focus tint
     */
    Color focusColor() {
        return color(UITokens.ACCENT, DEFAULT_ACCENT);
    }

    /**
     * Reads one cached resolved color with an explicit nonnull fallback.
     *
     * @param key semantic or component color key
     * @param fallback color for an unspecified theme role
     * @return shared resolved color or fallback
     */
    private Color color(StyleKey<Color> key, Color fallback) {
        ResolvedStyle style = getStyle();
        Color value = style == null ? null : style.get(key);
        return value == null ? fallback : value;
    }

    /**
     * Locates a stable path in the current cached generation.
     *
     * @param path directory target, possibly null
     * @return visible index, or -1 if no row represents it
     */
    private int indexOf(Path path) {
        if (path == null) return -1;
        for (int i = 0; i < visible.size(); i++)
            if (visible.get(i).path().equals(path)) return i;
        return -1;
    }

    /**
     * Exposes a row without moving the viewport if it is already visible.
     *
     * @param path directory to expose
     */
    private void revealRow(Path path) {
        int index = indexOf(path);
        if (index >= 0) list.revealIndex(index);
    }

    /**
     * Opens an owner menu after consuming the request and stabilizing row focus.
     *
     * @param path directory command target, or null for an empty-area request
     * @param press borrowed coordinates or keyboard-position metadata
     */
    private void requestContext(Path path, MousePressEvent press) {
        lastClickedPath = null;
        press.consume();
        if (path != null) focusRow(path);
        else {
            contextPath = null;
            if (getRoot() != null) getRoot()
                    .setFocusTo(this);
        }
        contextMenu.accept(path, press);
    }

    /**
     * Converts the focused row's lower leading edge to screen coordinates for a
     * keyboard menu request, accounting for viewport scaling and ancestor scroll.
     *
     * @param index focused visible row
     * @return coordinate-only metadata; never dispatched as a mouse input event
     */
    private MousePressEvent keyboardContext(int index) {
        list.revealIndex(index);
        if (getRoot() != null) getRoot()
                .layout();
        UINode row = list.getItemNode(index);
        if (row == null) row = this;
        Vector2f content = row.screenToContent(0, 0);
        Vector2f world = row.screenToWorld(0, 0);
        float x = row.getRenderX() - content.x() + world.x() + 32;
        float y = row.getRenderY() - content.y() + world.y();
        Viewport viewport = getRoot() == null ? null : getRoot().getViewport();
        if (viewport != null) {
            if (viewport.getCamera() != null) {
                x = (x - viewport.getCamera().getCenter().x()) * viewport.getCamera().getZoom() + viewport.getWorldWidth() * .5f;
                y = (y - viewport.getCamera().getCenter().y()) * viewport.getCamera().getZoom() + viewport.getWorldHeight() * .5f;
            }
            x = viewport.getX() + x * viewport.getWidth() / viewport.getWorldWidth();
            y = viewport.getY() + y * viewport.getHeight() / viewport.getWorldHeight();
        }
        return new MousePressEvent(Mouse.RIGHT, 0, Math.round(x), Math.round(y));
    }

    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();
        if (style != null) {
            Float height = style.get(ROW_HEIGHT_KEY);
            Float spacing = style.get(INDENT_KEY);
            Float size = style.get(FONT_SIZE_KEY);
            if (height != null && Float.isFinite(height) && height > 0 && rowHeight != height) {
                rowHeight = height;
                list.rowHeight(height);
            }
            if (spacing != null && Float.isFinite(spacing) && spacing >= 8) indent = spacing;
            if (size != null && Float.isFinite(size) && size > 0) fontSize = size;
        }
        super.applyLayout();
    }

    @Override
    public void onInputPreview(UIInputEvent context) {
        if (isDisabled()) return;
        if (!customContextMenu && fileMenu.handleShortcut(context)) return;
        if (context.event() instanceof MousePressEvent press && press.getButton() == Mouse.RIGHT) {
            for (UINode node = context.target(); node != null && node != this; node = node.getParent()) {
                if (node instanceof NanoDirectoryRow row) {
                    requestContext(row.getPath(), press);
                    return;
                }
            }
            if (contextDestination() != null) requestContext(null, press);
            return;
        }
        if (!(context.event() instanceof KeyPressEvent key)) return;
        lastClickedPath = null;
        int at = indexOf(focusedPath);
        if (at < 0) at = indexOf(contextPath);
        if (at < 0) at = indexOf(selected);
        if (at < 0 && !visible.isEmpty()) at = 0;
        if (at < 0) return;
        Path path = visible.get(at)
                .path();
        if (key.getKey() == Keyboard.MENU || key.getKey() == Keyboard.F10 && key.isShiftDown()) {
            key.consume();
            if (!key.isRepeat()) requestContext(path, keyboardContext(at));
            return;
        }
        switch (key.getKey()) {
            case Keyboard.RIGHT -> {
                key.consume();
                if (!expanded.contains(path)) expand(path);
                else if (at + 1 < visible.size() && visible.get(at + 1).depth() > visible.get(at).depth()) path = visible.get(at + 1).path();
            }
            case Keyboard.LEFT -> {
                key.consume();
                if (expanded.contains(path)) collapse(path);
                else if (visible.get(at).depth() > 0) {
                    int depth = visible.get(at)
                            .depth();
                    while (at > 0 && visible.get(at).depth() >= depth) at--;
                    path = visible.get(at).path();
                }
            }
            case Keyboard.DOWN -> path = visible.get(Math.min(visible.size() - 1, at + 1)).path();
            case Keyboard.UP -> path = visible.get(Math.max(0, at - 1)).path();
            case Keyboard.HOME -> path = visible.getFirst().path();
            case Keyboard.END -> path = visible.getLast().path();
            case Keyboard.ENTER, Keyboard.SPACE -> {
                key.consume();
                if (!key.isRepeat()) activateRow(path);
                return;
            }
            default -> {
                return;
            }
        }
        key.consume();
        focusRow(path);
        revealRow(path);
    }

    @Override
    public void update(float delta) {
        fileMenu.update();
        super.update(delta);
    }

    @Override
    public void onDestroy() {
        fileMenu.hide();
        super.onDestroy();
    }
}
