package valthorne.ui.behavior.file;

import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Performs explicit file explorer mutations without UI or engine dependencies.
 *
 * <p>Run potentially large operations on a worker thread and refresh the browser
 * on its UI thread afterward. Paste preserves existing entries by choosing
 * {@code name - Copy.ext} and numbered alternatives. Cutting into the original
 * directory is a no-op. Selections containing both a directory and its children
 * operate on the directory once. Symbolic links are copied or deleted as links;
 * directory traversal never follows them.</p>
 *
 * <p>Names are validated as single path components before any mutation. Directory
 * paste destinations are resolved to actual paths to reject copying or moving a
 * directory into itself through a symbolic-link alias. Cross-volume moves finish
 * copying before removing their source. Existing destination files are never
 * overwritten. Failures preserve their checked cause; completed entries and
 * partially copied trees may remain, so the caller should always refresh.</p>
 *
 * <p>{@link #moveToTrash(List)} exclusively uses the desktop's trash API and never
 * silently falls back to permanent deletion. A caller that chooses permanent
 * deletion must explicitly request {@link #deletePermanently(List)} after showing
 * its own confirmation. Instances are stateless and may be reused.</p>
 */
public final class FileOperations {

    /**
     * Creates a stateless service for explicit explorer mutations.
     */
    public FileOperations() {
    }

    /**
     * Copies or moves a selection into an existing directory, keeping both on name collisions.
     *
     * @param sources immutable source selection to process
     * @param destination existing directory receiving the selection
     * @param move whether to remove sources after successfully transferring them
     * @return immutable destination paths in effective selection order
     * @throws IOException if a source is unavailable, a destination is invalid, or transfer fails
     */
    public List<Path> paste(List<Path> sources, Path destination, boolean move) throws IOException {
        Path targetDirectory = requireDirectory(destination);
        ArrayList<Path> selection = effectiveSelection(sources);
        ArrayList<Path> targets = new ArrayList<>(selection.size());
        HashSet<Path> reserved = new HashSet<>(selection.size());

        // Validate every selected root before starting any transfer.
        for (Path source : selection) {
            if (source.getFileName() == null)
                throw new IOException("A filesystem root cannot be copied or moved.");
            BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isDirectory() && targetDirectory.startsWith(source.toRealPath()))
                throw new IOException("A directory cannot be copied or moved into itself: " + source);

            Path target = namedTarget(targetDirectory, source.getFileName().toString());
            if (move && Files.isSameFile(source.getParent(), targetDirectory)) {
                targets.add(source);
                reserved.add(source);
                continue;
            }
            target = availableTarget(target, attributes.isDirectory(), reserved);
            targets.add(target);
            reserved.add(target);
        }

        for (int i = 0; i < selection.size(); i++) {
            Path source = selection.get(i);
            Path target = targets.get(i);
            if (source.equals(target)) continue;
            if (move) move(source, target);
            else copy(source, target);
        }
        return List.copyOf(targets);
    }

    /**
     * Creates an empty file using a validated single-component name.
     *
     * @param parent existing destination directory
     * @param name desired leaf filename, including any extension
     * @return created absolute file path
     * @throws IOException if the name is invalid, already exists, or cannot be created
     */
    public Path createFile(Path parent, String name) throws IOException {
        Path target = namedTarget(requireDirectory(parent), name);
        return Files.createFile(target);
    }

    /**
     * Creates one folder using a validated single-component name.
     *
     * @param parent existing destination directory
     * @param name desired leaf folder name
     * @return created absolute directory path
     * @throws IOException if the name is invalid, already exists, or cannot be created
     */
    public Path createDirectory(Path parent, String name) throws IOException {
        Path target = namedTarget(requireDirectory(parent), name);
        return Files.createDirectory(target);
    }

    /**
     * Renames an entry within its original directory without replacing an existing entry.
     *
     * @param source existing file, directory, or symbolic link
     * @param name desired single-component leaf name
     * @return absolute path after renaming
     * @throws IOException if the name is invalid, collides with another entry, or renaming fails
     */
    public Path rename(Path source, String name) throws IOException {
        Path original = requireEntry(source);
        if (original.getParent() == null)
            throw new IOException("A filesystem root cannot be renamed.");
        Path target = namedTarget(original.getParent(), name);
        if (original.getFileName().toString().equals(name)) return original;
        if (original.equals(target)) {
            Path temporary = availableTarget(original.resolveSibling(original.getFileName() + " - Rename"), true, new HashSet<>());
            Files.move(original, temporary);
            try {
                return Files.move(temporary, target);
            } catch (IOException failure) {
                try {
                    Files.move(temporary, original);
                } catch (IOException restorationFailure) {
                    failure.addSuppressed(restorationFailure);
                }
                throw failure;
            }
        }
        return Files.move(original, target);
    }

    /**
     * Reports whether the platform supplies a recoverable trash operation.
     *
     * @return whether the desktop advertises MOVE_TO_TRASH support
     */
    public boolean isTrashSupported() {
        return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Action.MOVE_TO_TRASH);
    }

    /**
     * Moves selected entries into the platform trash without permanent-deletion fallback.
     *
     * @param sources files or directories requested for recoverable deletion
     * @throws IOException if trash is unavailable or any selected entry cannot be trashed
     */
    public void moveToTrash(List<Path> sources) throws IOException {
        ArrayList<Path> selection = effectiveSelection(sources);
        if (selection.isEmpty()) return;
        for (Path source : selection) requireRemovableEntry(source);
        if (!isTrashSupported())
            throw new IOException("The operating system does not provide a trash operation.");
        Desktop desktop = Desktop.getDesktop();
        for (Path source : selection) {
            try {
                if (!desktop.moveToTrash(source.toFile()))
                    throw new IOException("The entry could not be moved to the trash: " + source);
            } catch (UnsupportedOperationException | SecurityException failure) {
                throw new IOException("The entry could not be moved to the trash: " + source, failure);
            }
        }
    }

    /**
     * Permanently deletes a selection after the caller has obtained explicit UI confirmation.
     *
     * <p>Symbolic links are removed directly without visiting their targets. An
     * error interrupts deletion and leaves remaining entries available.</p>
     *
     * @param sources files or directories explicitly confirmed for permanent deletion
     * @throws IOException if any selected entry cannot be removed
     */
    public void deletePermanently(List<Path> sources) throws IOException {
        ArrayList<Path> selection = effectiveSelection(sources);
        for (Path source : selection) requireRemovableEntry(source);
        for (Path source : selection)
            Files.walkFileTree(source, new FileDeleteVisitor());
    }

    /**
     * Resolves an existing directory to its actual absolute location.
     *
     * @param directory requested target directory
     * @return actual directory location with symbolic-link aliases resolved
     * @throws IOException if the path does not identify an accessible directory
     */
    private Path requireDirectory(Path directory) throws IOException {
        Path resolved = Objects.requireNonNull(directory, "directory")
                .toRealPath();
        if (!Files.isDirectory(resolved))
            throw new IOException("The destination is not a directory: " + directory);
        return resolved;
    }

    /**
     * Normalizes and verifies an entry without following a symbolic link's final target.
     *
     * @param path requested source path
     * @return normalized absolute source path
     * @throws IOException if the source entry is unavailable
     */
    private Path requireEntry(Path path) throws IOException {
        Path absolute = Objects.requireNonNull(path, "path")
                .toAbsolutePath()
                .normalize();
        Files.readAttributes(absolute, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return absolute;
    }

    /**
     * Rejects filesystem roots before a destructive operation begins.
     *
     * @param source normalized existing source entry
     * @throws IOException if the requested entry is a filesystem root
     */
    private void requireRemovableEntry(Path source) throws IOException {
        if (source.getFileName() == null || source.getParent() == null)
            throw new IOException("A filesystem root cannot be deleted: " + source);
    }

    /**
     * Removes duplicate entries and descendants already covered by a selected real directory.
     *
     * @param sources user selection snapshot
     * @return effective normalized roots preserving their relative selection order
     * @throws IOException if a selected entry is unavailable
     */
    ArrayList<Path> effectiveSelection(List<Path> sources) throws IOException {
        Objects.requireNonNull(sources, "sources");
        ArrayList<Path> selection = new ArrayList<>(sources.size());
        for (Path path : sources) {
            Path source = requireEntry(path);
            boolean covered = false;
            for (int i = 0; i < selection.size(); i++) {
                Path existing = selection.get(i);
                if (source.equals(existing) || source.startsWith(existing) && Files.isDirectory(existing, LinkOption.NOFOLLOW_LINKS)) {
                    covered = true;
                    break;
                }
            }
            if (covered) continue;
            if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                for (int i = selection.size() - 1; i >= 0; i--)
                    if (selection.get(i).startsWith(source)) selection.remove(i);
            }
            selection.add(source);
        }
        return selection;
    }

    /**
     * Validates a user-entered name before resolving its destination path.
     *
     * @param parent normalized parent directory
     * @param name requested leaf name
     * @return child path that remains directly inside the parent
     * @throws IOException if the name is empty, invalid, or attempts path traversal
     */
    private Path namedTarget(Path parent, String name) throws IOException {
        Objects.requireNonNull(name, "name");
        if (name.isBlank() || name.equals(".") || name.equals("..") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0)
            throw new IOException("Enter a file or folder name without a directory path.");
        for (int i = 0; i < name.length(); i++)
            if (name.charAt(i) < 32) throw new IOException("File and folder names cannot contain control characters.");
        try {
            Path leaf = parent.getFileSystem()
                    .getPath(name);
            if (leaf.isAbsolute() || leaf.getNameCount() != 1)
                throw new IOException("The name must describe a single file or folder.");
            Path target = parent.resolve(leaf)
                    .normalize();
            if (!parent.equals(target.getParent()))
                throw new IOException("The name must remain inside the selected folder.");
            validateWindowsName(name);
            return target;
        } catch (InvalidPathException failure) {
            throw new IOException("The file or folder name is invalid: " + name, failure);
        }
    }

    /**
     * Rejects Windows aliases and device names that do not represent ordinary file entries.
     *
     * @param name already validated single-component name
     * @throws IOException if Windows cannot safely represent the requested name
     */
    private void validateWindowsName(String name) throws IOException {
        if (!System.getProperty("os.name", "").regionMatches(true, 0, "Windows", 0, 7)) return;
        if (name.endsWith(".") || name.endsWith(" "))
            throw new IOException("Windows file and folder names cannot end with a dot or space.");
        for (int i = 0; i < name.length(); i++)
            if ("<>:\"|?*".indexOf(name.charAt(i)) >= 0)
                throw new IOException("The name contains a character that Windows does not allow.");

        int extension = name.indexOf('.');
        String stem = extension < 0 ? name : name.substring(0, extension);
        if (stem.equalsIgnoreCase("CON") || stem.equalsIgnoreCase("PRN") || stem.equalsIgnoreCase("AUX") || stem.equalsIgnoreCase("NUL") || stem.equalsIgnoreCase("CONIN$") || stem.equalsIgnoreCase("CONOUT$"))
            throw new IOException("The name is reserved by Windows: " + name);
        if (stem.length() == 4 && (stem.regionMatches(true, 0, "COM", 0, 3) || stem.regionMatches(true, 0, "LPT", 0, 3))) {
            char suffix = stem.charAt(3);
            if (suffix >= '1' && suffix <= '9' || suffix == '\u00b9' || suffix == '\u00b2' || suffix == '\u00b3')
                throw new IOException("The name is reserved by Windows: " + name);
        }
    }

    /**
     * Chooses an unused keep-both name without overwriting an existing or reserved target.
     *
     * @param target requested destination path
     * @param directory whether the entry is a directory rather than an extension-bearing file
     * @param reserved destinations already assigned to this paste operation
     * @return unused destination candidate
     * @throws IOException if the collision suffix space is exhausted
     */
    private Path availableTarget(Path target, boolean directory, HashSet<Path> reserved) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !reserved.contains(target)) return target;
        String name = target.getFileName()
                .toString();
        int dot = directory ? -1 : name.lastIndexOf('.');
        if (dot <= 0) dot = name.length();
        String stem = name.substring(0, dot);
        String extension = name.substring(dot);
        Path parent = target.getParent();
        for (int index = 1; index > 0; index++) {
            String suffix = index == 1 ? " - Copy" : " - Copy (" + index + ')';
            Path candidate = parent.resolve(stem + suffix + extension);
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) && !reserved.contains(candidate)) return candidate;
        }
        throw new IOException("No unused destination name is available for " + target);
    }

    /**
     * Copies one selected root without following symbolic links or replacing existing entries.
     *
     * @param source existing source root
     * @param target unused destination root
     * @throws IOException if any part of the copy fails
     */
    void copy(Path source, Path target) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("The destination already exists: " + target);
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) && target.getParent().toRealPath().startsWith(source.toRealPath()))
            throw new IOException("A directory cannot be copied into itself: " + source);
        Files.walkFileTree(source, new FileCopyVisitor(source, target));
    }

    /**
     * Moves within one filesystem or completes a cross-volume copy before source removal.
     *
     * @param source existing source root
     * @param target unused destination root
     * @throws IOException if transfer or subsequent source removal fails
     */
    void move(Path source, Path target) throws IOException {
        if (source.equals(target) && !source.getFileName().toString().equals(target.getFileName().toString())) {
            rename(source, target.getFileName().toString());
            return;
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("The destination already exists: " + target);
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) && target.getParent().toRealPath().startsWith(source.toRealPath()))
            throw new IOException("A directory cannot be moved into itself: " + source);
        if (Files.getFileStore(source.getParent()).equals(Files.getFileStore(target.getParent()))) {
            Files.move(source, target);
            return;
        }
        copy(source, target);
        try {
            Files.walkFileTree(source, new FileDeleteVisitor());
        } catch (IOException failure) {
            throw new IOException("The destination was copied, but its source could not be completely removed: " + source, failure);
        }
    }
}
