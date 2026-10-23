package valthorne.ui.behavior.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Objects;

/**
 * Shared live identity of an entry referenced by several chronological actions.
 * Restoring a Trash backup or crossing filesystems changes provider identity;
 * updating this record keeps earlier actions usable without accepting unrelated
 * replacements. Access is confined to the history's serialized worker.
 */
final class FileHistoryIdentity {

    private Path path; // Current live or recovery location of this entry.
    private Object key; // Provider identity, or null when unavailable.
    private FileTime created; // Fallback creation time for providers lacking keys.

    /**
     * Identifies an existing entry without following its final symbolic link.
     *
     * @param path current entry location
     * @throws IOException if the entry is unavailable
     */
    FileHistoryIdentity(Path path) throws IOException {
        capture(path);
    }

    /**
     * Returns the location shared by all actions on this entry.
     *
     * @return current absolute location
     */
    Path path() {
        return path;
    }

    /**
     * Rejects a replacement while permitting edits to the same entry.
     *
     * @return whether this entry still has its recorded identity
     * @throws IOException if its attributes cannot be read
     */
    boolean matches() throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return key != null ? Objects.equals(key, attributes.fileKey()) : created.equals(attributes.creationTime());
    }

    /**
     * Refreshes identity after a history-owned move or restore.
     *
     * @param location completed entry location
     * @throws IOException if the completed entry cannot be identified
     */
    void capture(Path location) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(location, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        path = location;
        key = attributes.fileKey();
        created = attributes.creationTime();
    }
}
