package valthorne.ui.behavior.file;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import valthorne.browser.BrowserFiles;

/**
 * Shares immutable file copy/cut selections between browser workspace controls.
 * Browsers do not expose the operating system's file-list clipboard. This service
 * intentionally retains only virtual paths within the current application session;
 * text clipboard operations continue through the independent browser text adapter.
 * No OS clipboard permission is requested and changing UI selection never changes
 * a pending paste. All calls are confined to the browser UI coroutine.
 */
public final class FileClipboard {
    /**
     * Immutable shared workspace selection for the current browser session.
     */
    private static List<Path> paths = List.of();
    /**
     * Whether the current workspace selection requests a move on paste.
     */
    private static boolean cut;
    /**
     * Browser session owning the selection.
     */
    private static int session;

    /**
     * Creates a view over the current session's workspace clipboard.
     */
    public FileClipboard() {
        refreshSession();
    }

    /**
     * Publishes a normalized immutable copy selection.
     * @param paths selected workspace paths
     * @throws IOException retained shared API contract
     */
    public void copy(List<Path> paths) throws IOException {
        publish(paths, false);
    }

    /**
     * Publishes a normalized immutable cut selection without moving files yet.
     * @param paths selected workspace paths
     * @throws IOException retained shared API contract
     */
    public void cut(List<Path> paths) throws IOException {
        publish(paths, true);
    }

    /**
     * Returns the current immutable path selection.
     * @return current paths, empty when no selection exists
     * @throws IOException retained shared API contract
     */
    public List<Path> read() throws IOException {
        refreshSession();
        return paths;
    }

    /**
     * Captures paths and copy/cut intent from the same workspace selection.
     * @return immutable coherent selection
     * @throws IOException retained shared API contract
     */
    public FileClipboardSelection readSelection() throws IOException {
        refreshSession();
        return new FileClipboardSelection(paths, cut);
    }

    /**
     * Reports whether a pending workspace paste should move the source files.
     * @return current move intent
     * @throws IOException retained shared API contract
     */
    public boolean isCut() throws IOException {
        refreshSession();
        return cut;
    }

    /**
     * Clears this session's file selection and pending cut intent.
     * @throws IOException retained shared API contract
     */
    public void clear() throws IOException {
        refreshSession();
        paths = List.of();
        cut = false;
    }

    /**
     * Clears only the matching cut snapshot after a successful paste.
     * @param selected original source selection
     * @throws IOException retained shared API contract
     */
    public void clearIfMatches(List<Path> selected) throws IOException {
        refreshSession();
        if (cut && paths.equals(Objects.requireNonNull(selected, "paths"))) clear();
    }

    /**
     * Normalizes and snapshots a new selection without retaining mutable UI lists.
     * @param selected requested paths
     * @param move requested copy/cut intent
     */
    private void publish(List<Path> selected, boolean move) {
        refreshSession();
        Objects.requireNonNull(selected, "paths");
        ArrayList<Path> normalized = new ArrayList<>(selected.size());
        for (Path path : selected) normalized.add(Objects.requireNonNull(path, "path").toAbsolutePath().normalize());
        paths = List.copyOf(normalized);
        cut = move && !paths.isEmpty();
    }

    /**
     * Discards a previous application's pending selection on page relaunch.
     */
    private static void refreshSession() {
        /*
         * Virtual paths and cut intent must never leak across browser host lifetimes.
         */
        int current = BrowserFiles.session();
        if (current != session) {
            paths = List.of();
            cut = false;
            session = current;
        }
    }
}
