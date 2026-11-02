package valthorne.io.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import valthorne.browser.BrowserFiles;

/**
 * Browser implementation of the shared file-dialog configuration. Open and folder
 * selection import explicit user selections into the application's virtual workspace.
 * Save returns a workspace path registered for export when the application writes
 * it; File System Access grants are used where available, with a download fallback.
 * Cancellation returns null. No native OS paths, executables, or desktop dialogs
 * are accessible. Only the calling Java coroutine suspends while the picker is open.
 *
 * <p>The builder remains shared with desktop. The browser chooses picker placement
 * and permissions; defaultPath supplies only a suggested filename, not OS access.
 * File filters are filename-extension hints. Invalid selections and denied browser
 * operations propagate as UncheckedIOException. See BrowserFiles for save lifecycle.</p>
 */
public final class NativeFileDialog {
    private final String title; // User-facing picker caption.
    private final String defaultPath; // Suggested workspace path or filename.
    private final String[] filterPatterns; // Owned immutable file-extension hints.

    /**
     * Retains validated settings supplied by the shared builder.
     * @param title user-facing caption
     * @param defaultPath optional suggested path
     * @param filterDescription desktop filter label, represented by extension hints here
     * @param filterPatterns owned immutable wildcard patterns
     */
    NativeFileDialog(String title, String defaultPath, String filterDescription, String[] filterPatterns) {
        this.title = title;
        this.defaultPath = defaultPath;
        this.filterPatterns = filterPatterns;
    }

    /**
     * Starts shared fluent configuration without requesting browser permissions.
     * @return new mutable builder
     */
    public static NativeFileDialogBuilder builder() {
        /*
         * Browser access is requested only when a configured picker is displayed.
         */
        return new NativeFileDialogBuilder();
    }

    /**
     * Imports one selected file and returns its sandbox path.
     * @return selected workspace file or null on cancellation
     */
    public Path open() {
        return choose(0);
    }

    /**
     * Chooses a save grant or download filename without writing application data.
     * @return workspace destination monitored for application writes, or null
     */
    public Path save() {
        return choose(1);
    }

    /**
     * Imports a selected directory tree and returns its workspace root.
     * @return imported workspace directory or null on cancellation
     */
    public Path selectFolder() {
        return choose(2);
    }

    /**
     * Suspends the caller until one browser picker resolves or fails.
     * @param mode browser picker operation
     * @return chosen workspace path or null
     * @throws UncheckedIOException if selection or import fails
     */
    private Path choose(int mode) {
        try {
            return BrowserFiles.choose(mode, title, defaultPath, filterPatterns);
        } catch (IOException failure) {
            throw new UncheckedIOException("Browser file dialog failed", failure);
        }
    }
}
