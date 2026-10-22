package valthorne.ui.behavior.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Session undo/redo history for one file-explorer command controller.
 * Copy, move, import, rename, creation, and native Trash deletion are recorded
 * as user actions. A new successful mutation abandons forward history. Permanent
 * deletion is an irreversible barrier and must call {@link #forget()} first.
 *
 * <p>The controller serializes all mutations on its worker and reads menu state
 * only while that worker is idle. Replay validates every entry before starting;
 * destination collisions are errors rather than overwrite requests. If replay
 * fails, completed steps are reversed where possible and rollback failures are
 * attached to the original exception. Partial original operations retain their
 * successfully completed entries as one undoable action.</p>
 *
 * <p>Recovery content lives in a lazily allocated private temporary directory.
 * Undoing creation/copy preserves subsequent edits there for redo. Native Trash
 * deletion requires a recovery copy because Java's desktop API has no restore
 * token. Recovery files are intentionally never automatically deleted: abandoned
 * redo content and copies from interrupted transfers remain recoverable from
 * {@link #getRecoveryDirectory()}. History is local to this session, not persisted
 * across application restarts. No work occurs during rendering.</p>
 */
public final class FileOperationHistory {

    private final FileOperations operations = new FileOperations(); // Stateless filesystem operations.
    private final ArrayList<FileHistoryOperation> history = new ArrayList<>(); // Chronological session actions.
    private int cursor; // Number of actions currently applied.
    private Path storage; // Recovery directory, or null before recovery content is needed.

    /**
     * Creates an empty history without allocating disk storage.
     */
    public FileOperationHistory() {
    }

    /**
     * Returns the next backward action caption while the worker is idle.
     *
     * @return action caption, or an empty string when unavailable
     */
    public String undoName() {
        return cursor == 0 ? "" : history.get(cursor - 1).name();
    }

    /**
     * Returns the next forward action caption while the worker is idle.
     *
     * @return action caption, or an empty string when unavailable
     */
    public String redoName() {
        return cursor == history.size() ? "" : history.get(cursor).name();
    }

    /**
     * Exposes recovery storage for explicit application recovery or housekeeping.
     * Never remove this directory while its history remains usable.
     *
     * @return private session recovery directory, or null before allocation
     */
    public Path getRecoveryDirectory() {
        return storage;
    }

    /**
     * Transfers an effective selection, grouping completed roots into one action.
     *
     * @param sources captured sources
     * @param destination existing receiving directory
     * @param move whether to transfer source ownership
     * @param name user-facing action caption
     * @throws IOException if transfer fails; completed entries remain undoable
     */
    public void paste(List<Path> sources, Path destination, boolean move, String name) throws IOException {
        ArrayList<Path> selected = operations.effectiveSelection(sources);
        // Validate the entire selection before performing even the first root.
        Path realDestination = destination.toRealPath();
        if (!Files.isDirectory(realDestination)) throw new IOException("The destination is not a folder.");
        for (Path source : selected) {
            if (source.getParent() == null) throw new IOException("A filesystem root cannot be copied or moved.");
            if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) && realDestination.startsWith(source.toRealPath()))
                throw new IOException("A directory cannot be copied or moved into itself: " + source);
        }
        ArrayList<FileHistoryEntry> entries = new ArrayList<>(selected.size());
        try {
            for (Path source : selected) {
                Path original = source.getParent().toRealPath().resolve(source.getFileName());
                FileHistoryIdentity identity = move ? identity(original) : null;
                Path target = operations.paste(List.of(source), destination, move).getFirst();
                if (move && original.equals(target)) continue;
                entries.add(new FileHistoryEntry(operations, move ? original : null, target, null, identity));
                if (move) relocateDescendants(original, target);
            }
        } finally {
            record(name, entries);
        }
    }

    /**
     * Creates and records an empty file or folder.
     *
     * @param parent receiving directory
     * @param name validated by the filesystem operation
     * @param directory whether to create a folder
     * @throws IOException if creation fails
     */
    public void create(Path parent, String name, boolean directory) throws IOException {
        Path target = directory ? operations.createDirectory(parent, name) : operations.createFile(parent, name);
        record(directory ? "New Folder" : "New File", List.of(new FileHistoryEntry(operations, null, target, null, null)));
    }

    /**
     * Renames and records one entry, including Windows case-only changes.
     *
     * @param source existing entry
     * @param name desired leaf name
     * @throws IOException if the rename fails
     */
    public void rename(Path source, String name) throws IOException {
        Path original = source.getParent().toRealPath().resolve(source.getFileName());
        FileHistoryIdentity identity = identity(original);
        Path target = operations.rename(source, name);
        if (original.getFileName().toString().equals(target.getFileName().toString())) return;
        record("Rename", List.of(new FileHistoryEntry(operations, original, target, null, identity)));
        relocateDescendants(original, target);
    }

    /**
     * Copies recoverable content before each native Trash request and records
     * completed deletions together. A failed snapshot never deletes its source.
     *
     * @param sources selected deletion roots
     * @throws IOException if backup or native deletion fails
     */
    public void trash(List<Path> sources) throws IOException {
        if (!operations.isTrashSupported()) throw new IOException("The operating system does not provide a trash operation.");
        ArrayList<Path> selected = operations.effectiveSelection(sources);
        for (Path source : selected)
            if (source.getParent() == null) throw new IOException("A filesystem root cannot be deleted.");
        ArrayList<FileHistoryEntry> entries = new ArrayList<>(selected.size());
        try {
            for (Path source : selected) {
                Path original = source.getParent().toRealPath().resolve(source.getFileName());
                FileHistoryIdentity identity = identity(original);
                Path backup = Files.createTempDirectory(storage(), "deleted-").resolve(source.getFileName());
                operations.copy(source, backup);
                operations.moveToTrash(List.of(source));
                entries.add(new FileHistoryEntry(operations, null, original, backup, identity));
                relocateDescendants(original, backup);
            }
        } finally {
            record("Delete", entries);
        }
    }

    /**
     * Reverses the most recently applied action, keeping its replay data.
     *
     * @throws IOException if replay or rollback fails
     */
    public void undo() throws IOException {
        if (cursor == 0) return;
        replay(history.get(cursor - 1), true);
        cursor--;
    }

    /**
     * Reapplies the most recently undone action at its exact previous locations.
     *
     * @throws IOException if replay or rollback fails
     */
    public void redo() throws IOException {
        if (cursor == history.size()) return;
        replay(history.get(cursor), false);
        cursor++;
    }

    /**
     * Forgets action references before an irreversible operation or policy change.
     * Retained recovery content remains on disk and is never silently destroyed.
     */
    public void forget() {
        history.clear();
        cursor = 0;
    }

    /**
     * Adds completed roots and abandons redo only after a real mutation.
     *
     * @param name user-facing action caption
     * @param entries completed mutation roots
     */
    private void record(String name, List<FileHistoryEntry> entries) {
        if (entries.isEmpty()) return;
        if (cursor < history.size()) history.subList(cursor, history.size()).clear();
        history.add(new FileHistoryOperation(name, List.copyOf(entries)));
        cursor++;
    }

    /**
     * Finds the live identity of an earlier action on an unchanged source entry.
     * Replaced entries begin an independent history identity.
     *
     * @param source canonical source path before the next mutation
     * @return prior shared identity, or null for an independently owned entry
     * @throws IOException if matching source attributes are inaccessible
     */
    private FileHistoryIdentity identity(Path source) throws IOException {
        for (int index = cursor - 1; index >= 0; index--) {
            for (FileHistoryEntry entry : history.get(index).entries()) {
                FileHistoryIdentity candidate = entry.identity();
                if (candidate.path().equals(source) && candidate.matches()) return candidate;
            }
        }
        return null;
    }

    /**
     * Updates earlier child actions after a history-owned parent transfer or restore.
     * Missing children remain invalid rather than being recreated implicitly.
     *
     * @param original prior parent location
     * @param target completed parent location
     * @throws IOException if an existing transferred child's attributes are inaccessible
     */
    private void relocateDescendants(Path original, Path target) throws IOException {
        for (FileHistoryOperation operation : history) {
            for (FileHistoryEntry entry : operation.entries()) {
                FileHistoryIdentity identity = entry.identity();
                Path path = identity.path();
                if (path.equals(original) || !path.startsWith(original)) continue;
                Path relocated = target.resolve(original.relativize(path));
                if (Files.exists(relocated, LinkOption.NOFOLLOW_LINKS)) identity.capture(relocated);
            }
        }
    }

    /**
     * Allocates one private recovery location on first use.
     *
     * @return existing private recovery directory
     * @throws IOException if temporary storage cannot be created
     */
    private Path storage() throws IOException {
        if (storage == null) storage = Files.createTempDirectory("valthorne-file-history-").toRealPath();
        return storage;
    }

    /**
     * Preflights a grouped replay and rolls completed steps back after failure.
     *
     * @param operation action snapshot
     * @param undo whether to run backward in reverse root order
     * @throws IOException if replay fails, retaining rollback failure causes
     */
    private void replay(FileHistoryOperation operation, boolean undo) throws IOException {
        List<FileHistoryEntry> entries = operation.entries();
        for (FileHistoryEntry entry : entries) entry.validate(undo);
        Path recovery = storage();
        int completed = 0;
        try {
            for (; completed < entries.size(); completed++) {
                int index = undo ? entries.size() - completed - 1 : completed;
                replayEntry(entries.get(index), undo, recovery);
            }
        } catch (IOException failure) {
            for (int i = completed - 1; i >= 0; i--) {
                int index = undo ? entries.size() - i - 1 : i;
                try {
                    replayEntry(entries.get(index), !undo, recovery);
                } catch (IOException rollback) {
                    failure.addSuppressed(rollback);
                }
            }
            throw failure;
        }
    }

    /**
     * Replays one root and refreshes identity records for its retained children.
     *
     * @param entry completed root action
     * @param undo whether to reverse the action
     * @param recovery private replay storage
     * @throws IOException if transfer or descendant identity refresh fails
     */
    private void replayEntry(FileHistoryEntry entry, boolean undo, Path recovery) throws IOException {
        Path original = entry.identity().path();
        entry.replay(undo, recovery);
        relocateDescendants(original, entry.identity().path());
    }
}
