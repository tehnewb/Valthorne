package valthorne.ui.nodes;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Horizontal ancestor navigation from a configured root to a current path. Buttons
 * emit absolute normalized paths; activation does not itself access the filesystem
 * or change the displayed path. This lets a browser validate navigation first.
 * <pre>{@code
 * BreadcrumbBar crumbs = new BreadcrumbBar().path(projectRoot, currentDirectory);
 * crumbs.onNavigate(path -> requestDirectory(path));
 * }</pre>
 * The bar uses lexical containment only and is not a filesystem permission boundary.
 * Place it in a horizontal ScrollPanel when arbitrarily long paths must fit.
 * @author Albert Beaupre
 */
public class BreadcrumbBar extends Panel {
    private Path path; // Last displayed absolute normalized location, or null before configuration.
    private Consumer<Path> navigate = value -> {}; // Synchronous requested-ancestor callback.

    /**
     * Creates an empty horizontal row whose buttons use normal theme and input routing.
     */
    public BreadcrumbBar() { getLayout().row().height(32).noShrink(); }

    /**
     * Rebuilds ancestor buttons after checking lexical containment. No directories
     * are opened; filesystem validation remains the caller's responsibility.
     * @param root first displayed ancestor
     * @param current root or one of its descendants
     * @return this bar
     * @throws IllegalArgumentException if current lies outside root
     */
    public BreadcrumbBar path(Path root, Path current) {
        Path base = Objects.requireNonNull(root).toAbsolutePath().normalize();
        Path next = Objects.requireNonNull(current).toAbsolutePath().normalize();
        if (!next.startsWith(base)) throw new IllegalArgumentException("Path is outside breadcrumb root");
        clear(); path = next;
        append(base);
        Path cursor = base;
        for (Path part : base.relativize(next)) {
            if (part.toString().isEmpty()) continue;
            cursor = cursor.resolve(part); append(cursor);
        }
        markLayoutDirty(); return this;
    }

    /**
     * Adds one ancestor button whose callback retains the immutable Path value.
     * @param ancestor absolute ancestor to display and emit
     */
    private void append(Path ancestor) {
        String name = ancestor.getFileName() == null ? ancestor.toString() : ancestor.getFileName().toString();
        Button button = new Button(name).action(b -> { if (isEnabled()) navigate.accept(ancestor); });
        button.getLayout().heightPercent(100).minWidth(40).padding(6).noShrink(); add(button);
    }

    /**
     * Reads the immutable currently displayed path without querying the filesystem.
     * @return normalized path or null before configuration
     */
    public Path getPath() { return path; }

    /**
     * Replaces the callback for requested navigation, without changing displayed state.
     * @param listener nonnull synchronous listener
     * @return this bar
     */
    public BreadcrumbBar onNavigate(Consumer<Path> listener) { navigate = Objects.requireNonNull(listener); return this; }
}
