package valthorne.browser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Provides recoverable deletion inside the browser's virtual workspace. Each trash
 * entry owns its moved contents and the original absolute virtual path. No OS trash
 * or user filesystem is accessed. Entries remain visible under /workspace/.trash
 * until explicitly restored or removed by application file controls. Calls are
 * confined to the application coroutine; moves occur within the virtual provider.
 */
public final class BrowserTrash {
    /**
     * Prevents constructing stateless workspace trash services.
     */
    private BrowserTrash() {
    }

    /**
     * Moves one virtual entry into recoverable workspace trash.
     * @param source existing virtual file or directory outside the trash tree
     * @return trash record directory accepted by restore
     * @throws IOException if the source is protected, unavailable, or cannot be moved
     */
    public static Path move(Path source) throws IOException {
        /*
         * Persist the recovery path before moving contents. Reject roots and trash
         * ancestors so the recovery metadata can never be moved into its own payload.
         */
        Path original = source.toAbsolutePath().normalize();
        Path trash = BrowserFiles.home().resolve(".trash");
        if (original.getParent() == null || original.startsWith(trash) || trash.startsWith(original))
            throw new IOException("The virtual filesystem root or trash cannot be trashed");
        BrowserFiles.createDirectories(trash);
        Path record = Files.createTempDirectory(trash, "item-");
        Path metadata = record.resolve("original-path");
        try {
            Files.write(metadata, original.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(original, record.resolve("contents"));
            return record;
        } catch (IOException failure) {
            try {
                Files.deleteIfExists(metadata);
                Files.deleteIfExists(record);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    /**
     * Restores a trash entry to its recorded original path without overwriting files.
     * @param record record directory returned by move
     * @return restored original virtual path
     * @throws IOException if the record is invalid or the original location is occupied
     */
    public static Path restore(Path record) throws IOException {
        /*
         * Revalidate metadata because users can edit the virtual workspace directly.
         * Restoration never replaces another entry or targets the trash tree itself.
         */
        Path trash = BrowserFiles.home().resolve(".trash");
        Path entry = record.toAbsolutePath().normalize();
        if (!trash.equals(entry.getParent())) throw new IOException("Invalid browser trash record");
        Path metadata = entry.resolve("original-path");
        Path original = Path.of(new String(Files.readAllBytes(metadata), StandardCharsets.UTF_8)).toAbsolutePath().normalize();
        if (original.getParent() == null || original.startsWith(trash) || trash.startsWith(original))
            throw new IOException("Invalid browser trash destination");
        if (Files.exists(original)) throw new IOException("The original location is occupied: " + original);
        Files.move(entry.resolve("contents"), original);
        Files.delete(metadata);
        Files.delete(entry);
        return original;
    }
}
