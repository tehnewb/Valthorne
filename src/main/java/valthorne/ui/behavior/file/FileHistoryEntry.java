package valthorne.ui.behavior.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

/**
 * Tracks one reversible filesystem entry, retaining its identity across moves.
 * Creation and copy undo move the entry into recovery storage, preserving edits
 * for redo. Trash deletion keeps a separate recovery copy because the portable
 * desktop API cannot return a restore token. All access is worker-confined.
 */
final class FileHistoryEntry {

    private final FileOperations operations; // Borrowed filesystem implementation.
    private final Path original; // Original location, or null for created/copied entries.
    private final Path target; // Exact destination of the original operation.
    private final boolean trash; // Whether forward replay sends the entry to system Trash.
    private Path recovery; // Private recovery location, allocated when needed.
    private final FileHistoryIdentity identity; // Shared live identity retained across chronological actions.

    /**
     * Captures a completed entry without following its final symbolic link.
     *
     * @param operations borrowed filesystem service
     * @param original prior path, or null for copy/creation
     * @param target completed destination, or deleted source for Trash
     * @param recovery backup for an already completed Trash operation, or null
     * @param identity prior live identity, or null for a newly created/copied entry
     * @throws IOException if the completed entry cannot be identified
     */
    FileHistoryEntry(FileOperations operations, Path original, Path target, Path recovery, FileHistoryIdentity identity) throws IOException {
        this.operations = operations;
        this.original = original;
        this.target = target;
        this.recovery = recovery;
        trash = recovery != null;
        Path location = trash ? recovery : target;
        this.identity = identity == null ? new FileHistoryIdentity(location) : identity;
        this.identity.capture(location);
    }

    /**
     * Returns the live identity shared by related chronological actions.
     *
     * @return shared entry identity
     */
    FileHistoryIdentity identity() {
        return identity;
    }

    /**
     * Checks entry identity and the exact destination before replay begins.
     *
     * @param undo whether this is backward replay
     * @throws IOException if another program replaced the entry or occupied its destination
     */
    void validate(boolean undo) throws IOException {
        Path current = identity.path();
        if (!current.getParent().toRealPath().equals(current.getParent()))
            throw new IOException("The item's containing folder has changed: " + current);
        if (!identity.matches())
            throw new IOException("This item has been replaced outside the browser: " + current);
        Path destination = undo ? (trash ? target : original) : target;
        if (trash && !undo) return;
        if (destination != null && !current.equals(destination) && Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Undo/redo cannot replace an existing item: " + destination);
        if (destination != null && !Files.isDirectory(destination.getParent()))
            throw new IOException("The original destination folder is unavailable: " + destination.getParent());
        if (destination != null && !destination.getParent().toRealPath().equals(destination.getParent()))
            throw new IOException("The destination folder has changed: " + destination.getParent());
    }

    /**
     * Moves the retained entry back or forward without replacing other entries.
     * Trash redo snapshots the current content before requesting native deletion.
     *
     * @param undo whether to reverse the original operation
     * @param storage lazily created recovery directory for this history
     * @throws IOException if the filesystem or native Trash operation fails
     */
    void replay(boolean undo, Path storage) throws IOException {
        validate(undo);
        Path current = identity.path();
        if (trash && !undo) {
            Path backup = Files.createTempDirectory(storage, "deleted-").resolve(target.getFileName());
            operations.copy(current, backup);
            operations.moveToTrash(List.of(current));
            recovery = backup;
            identity.capture(backup);
            return;
        }
        Path destination;
        if (!undo || trash) destination = target;
        else if (original != null) destination = original;
        else {
            if (recovery == null) recovery = Files.createTempDirectory(storage, "retained-").resolve(target.getFileName());
            destination = recovery;
        }
        operations.move(current, destination);
        identity.capture(destination);
    }
}
