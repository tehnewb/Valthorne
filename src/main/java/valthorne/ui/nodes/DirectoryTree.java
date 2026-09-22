package valthorne.ui.nodes;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.ui.UIInputEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/**
 * Virtualized filesystem directory tree with lazy expansion and synchronized selection.
 * Roots are application-supplied places or drives; expanding a node enumerates only its
 * immediate nonhidden child directories. Symbolic links are omitted from expansion to
 * prevent cycles. Failed scans preserve the previous tree and notify the error listener.
 * <pre>{@code
 * DirectoryTree tree = new DirectoryTree(List.of(Path.of(System.getProperty("user.home"))));
 * tree.onNavigate(path -> browser.navigate(path));
 * tree.reveal(browser.getDirectory());
 * }</pre>
 * All operations run on the UI thread. This is a local snapshot, not a filesystem watch.
 * @author Albert Beaupre
 */
public class DirectoryTree extends Panel {
    /**
     * One visible flattened branch; depth controls indentation independently of path length.
     * @param path absolute directory represented by this row
     * @param depth zero for a root, increasing for each expanded ancestor
     * @author Albert Beaupre
     */
    public record Branch(Path path, int depth) {}

    private final List<Path> roots; // Ordered immutable places and filesystem roots.
    private final Map<Path, List<Path>> children = new HashMap<>(); // Successfully enumerated immediate children.
    private final Set<Path> expanded = new HashSet<>(); // Expanded branches retained across navigation.
    private final VirtualList list; // Bounded materialization of flattened rows.
    private List<Branch> visible = List.of(); // Immutable current depth-first row snapshot.
    private Path selected; // Canonical selected directory after successful navigation.
    private Consumer<Path> navigate = path -> {}; // Directory activation request handled by the owner.
    private Consumer<IOException> error = failure -> {}; // Scan-error notification without partial tree mutation.

    /**
     * Copies distinct absolute places without eagerly scanning their contents.
     * @param roots ordered nonnull directory places, typically Home, favorites, and drives
     */
    public DirectoryTree(List<Path> roots) {
        this.roots = roots.stream().map(path -> path.toAbsolutePath().normalize()).distinct().toList();
        getLayout().column().minWidth(0).minHeight(0);
        setStyleName("chooser-pane");
        list = new VirtualList(0, this::row); list.rowHeight(28).gap(0);
        list.getLayout().height(0).grow(1).minHeight(0).widthPercent(100);
        add(list); rebuild();
    }

    /**
     * Builds a left-aligned directory row with an independent disclosure button.
     * @param index visible branch position
     * @return owned row for the current flattened generation
     */
    private Button row(int index) {
        Branch branch = visible.get(index); Path path = branch.path();
        String name = path.getFileName() == null ? "This PC (" + path + ")" : path.getFileName().toString();
        Button row = new Button(name).action(button -> navigate.accept(path)); row.setStyleName("chooser-row");
        row.setSelected(path.equals(selected));
        row.getLabel().getLayout().absolute().left(44 + branch.depth() * 16).top(6);
        Button disclosure = new Button(expanded.contains(path) ? "v" : ">").action(button -> {
            if (expanded.contains(path)) collapse(path); else expand(path);
        });
        disclosure.setStyleName("chooser-tool");
        disclosure.getLayout().absolute().left(branch.depth() * 16).top(2).width(20).height(24);
        FileIcon icon = new FileIcon(true); icon.getLayout().absolute().left(23 + branch.depth() * 16).top(5).width(16).height(18);
        row.add(disclosure, icon); return row;
    }

    /**
     * Enumerates and expands one directory transactionally, retaining prior rows on failure.
     * @param path directory to expand
     * @return whether enumeration succeeded
     */
    public boolean expand(Path path) {
        if (isDisabled()) return false;
        path = path.toAbsolutePath().normalize();
        try {
            ArrayList<Path> found = new ArrayList<>();
            try (var stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) if (!Files.isSymbolicLink(child) && Files.isDirectory(child) && !Files.isHidden(child)) found.add(child);
            } catch (java.nio.file.DirectoryIteratorException failure) { throw failure.getCause(); }
            found.sort(Comparator.comparing(item -> item.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
            children.put(path, List.copyOf(found)); expanded.add(path); rebuild(); return true;
        } catch (IOException | SecurityException failure) {
            error.accept(failure instanceof IOException io ? io : new IOException(failure)); return false;
        }
    }

    /**
     * Collapses a branch while retaining descendant expansion state for later reopening.
     * @param path branch to collapse
     */
    public void collapse(Path path) { if (!isDisabled()) { expanded.remove(path.toAbsolutePath().normalize()); rebuild(); } }

    /**
     * Selects and reveals a path beneath the closest matching place. Only ancestors are
     * expanded; selection is updated without requesting navigation back to the owner.
     * @param path successfully visited directory
     */
    public void reveal(Path path) {
        selected = path.toAbsolutePath().normalize();
        Path best = null;
        for (Path root : roots) if (selected.startsWith(root) && (best == null || root.getNameCount() > best.getNameCount())) best = root;
        if (best != null) {
            Path cursor = best;
            for (Path part : best.relativize(selected)) {
                if (part.toString().isEmpty()) break;
                if (!expanded.contains(cursor) && !expand(cursor)) break;
                Path child = cursor.resolve(part);
                if (!children.getOrDefault(cursor, List.of()).contains(child)) {
                    ArrayList<Path> siblings = new ArrayList<>(children.getOrDefault(cursor, List.of()));
                    siblings.add(child); siblings.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));
                    children.put(cursor, List.copyOf(siblings));
                }
                cursor = child;
            }
        }
        rebuild();
        for (int i = 0; i < visible.size(); i++) if (visible.get(i).path().equals(selected)) { list.scrollToIndex(i); break; }
    }

    /**
     * Rebuilds the immutable flattened snapshot and invalidates only virtualized row nodes.
     */
    private void rebuild() {
        ArrayList<Branch> rows = new ArrayList<>(); for (Path root : roots) append(rows, root, 0);
        visible = List.copyOf(rows); list.itemCount(visible.size()); list.refreshItems();
    }

    /**
     * Traverses cached children only, avoiding filesystem queries during layout and drawing.
     * @param rows output list
     * @param path branch path
     * @param depth indentation level
     */
    private void append(List<Branch> rows, Path path, int depth) {
        rows.add(new Branch(path, depth));
        if (expanded.contains(path)) for (Path child : children.getOrDefault(path, List.of())) append(rows, child, depth + 1);
    }

    /**
     * Returns the visible snapshot for inspection without transferring tree ownership.
     * @return immutable depth-first branches
     */
    public List<Branch> getVisibleBranches() { return visible; }

    /**
     * Reads the last revealed directory; expansion alone does not alter selection.
     * @return selected directory or null before initial reveal
     */
    public Path getSelected() { return selected; }

    /**
     * Exposes the owned virtual viewport for focus and accessibility integration.
     * @return tree row list
     */
    public VirtualList getList() { return list; }

    /**
     * Replaces the activation listener; callers commit selection by calling reveal after navigation.
     * @param listener nonnull directory navigation consumer
     * @return this tree
     */
    public DirectoryTree onNavigate(Consumer<Path> listener) { navigate = Objects.requireNonNull(listener); return this; }

    /**
     * Replaces the scan-error listener. Errors never replace the current visible snapshot.
     * @param listener nonnull failure consumer
     * @return this tree
     */
    public DirectoryTree onError(Consumer<IOException> listener) { error = Objects.requireNonNull(listener); return this; }

    /**
     * Implements tree arrow navigation: Right expands, Left collapses or selects a parent,
     * Up/Down move through visible branches, and Enter activates the focused branch.
     * @param context routed preview event
     */
    @Override public void onInputPreview(UIInputEvent context) {
        if (isDisabled() || !(context.event() instanceof KeyPressEvent key)) return;
        int at = -1;
        for (int i = 0; i < visible.size(); i++) {
            var row = list.getItemNode(i);
            for (var node = context.target(); node != null && node != this; node = node.getParent())
                if (node == row) { at = i; break; }
            if (at >= 0) break;
        }
        if (at < 0) return;
        Path path = visible.get(at).path();
        switch (key.getKey()) {
            case Keyboard.RIGHT -> expand(path);
            case Keyboard.LEFT -> {
                if (expanded.contains(path)) collapse(path);
                else if (at > 0) { int depth = visible.get(at).depth(); while (at > 0 && visible.get(at).depth() >= depth) at--; }
            }
            case Keyboard.DOWN -> at = Math.min(visible.size() - 1, at + 1);
            case Keyboard.UP -> at = Math.max(0, at - 1);
            case Keyboard.HOME -> at = 0;
            case Keyboard.END -> at = visible.size() - 1;
            default -> { return; }
        }
        context.consume(); list.scrollToIndex(at);
        if (getRoot() != null) { getRoot().layout(); getRoot().setFocusTo(list.getItemNode(at)); }
    }
}
