package valthorne.utility;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.WatchEvent;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * <h1>FileUtility</h1>
 * A comprehensive collection of static methods for advanced file and
 * directory operations beyond what's provided by java.nio.file.Files,
 * such as extension manipulation, recursive traversal, archiving,
 * checksum computation, and more.
 * <p>
 * Note: For simple one-line operations (e.g., checking readability,
 * obtaining file size, creating temp files, etc.), prefer calling
 * java.nio.file.Files or Paths APIs directly rather than using
 * utility wrappers.
 * </p>
 *
 * @author Albert Beaupre
 */
public class FileUtility {

    /**
     * Returns the extension of the given file name.
     * <p>
     * If {@code fileName} is null, empty, has no '.', or the last '.' is
     * at the beginning or end, returns an empty string.
     * </p>
     *
     * @param fileName the file name (with or without path)
     * @return extension without dot, or empty string
     */
    public static String getExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 1 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1);
    }

    /**
     * Overload: accepts a {@link File} instance.
     *
     * @param file the file; must not be null
     * @return extension without dot, or empty string
     */
    public static String getExtension(File file) {
        Objects.requireNonNull(file, "file must not be null");
        return getExtension(file.getName());
    }

    /**
     * Overload: accepts a {@link Path} instance.
     *
     * @param path the path; must not be null
     * @return extension without dot, or empty string
     */
    public static String getExtension(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        return getExtension(path.getFileName().toString());
    }

    /**
     * Returns the base name (up to last dot) of a file name.
     *
     * @param fileName the file name; may be null
     * @return name before last dot, or null if input was null
     */
    public static String getBaseName(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }

    /**
     * Removes all extensions (after first dot) from a file name.
     *
     * @param fileName the file name; may be null
     * @return name before first dot, or null if input was null
     */
    public static String removeExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dotIndex = fileName.indexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }

    /**
     * Changes the extension of a file name.
     *
     * @param fileName original name; may be null
     * @param newExt   new extension without dot (leading dot tolerated)
     * @return updated name, or null if original was null
     */
    public static String changeExtension(String fileName, String newExt) {
        if (fileName == null) {
            return null;
        }
        String base = getBaseName(fileName);
        if (newExt == null || newExt.isEmpty()) {
            return base;
        }
        String normalized = newExt.startsWith(".") ? newExt.substring(1) : newExt;
        return base + '.' + normalized;
    }

    /**
     * Returns the parent directory path of a file path string.
     *
     * @param filePath raw path string; may be null
     * @return parent path or empty string if none, null if input was null
     */
    public static String getParentPath(String filePath) {
        if (filePath == null) {
            return null;
        }
        Path p = Paths.get(filePath).getParent();
        return (p == null) ? "" : p.toString();
    }

    /**
     * Creates directory (and parents) if it does not exist.
     *
     * @param dir target directory; must not be null
     * @throws IOException if creation fails
     */
    public static void ensureDirectoryExists(Path dir) throws IOException {
        Objects.requireNonNull(dir, "dir must not be null");
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    /**
     * Deletes a file/directory quietly, returning false on error.
     *
     * @param path target to delete; must not be null
     * @return true if deleted or did not exist, false on failure
     */
    public static boolean deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Recursively deletes a directory and all contents.
     *
     * @param path root; must not be null
     * @throws IOException if deletion fails
     */
    public static void deleteRecursively(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        if (Files.notExists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Lists files directly under a directory (non-recursive).
     *
     * @param dir directory; must not be null
     * @return list of files, empty if none
     * @throws IOException if listing fails
     */
    public static List<Path> listFiles(Path dir) throws IOException {
        Objects.requireNonNull(dir, "dir must not be null");
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            List<Path> list = new ArrayList<>();
            for (Path p : ds) {
                if (Files.isRegularFile(p)) {
                    list.add(p);
                }
            }
            return list;
        }
    }

    /**
     * Recursively lists all files under a directory.
     *
     * @param dir root; must not be null
     * @return list of file paths
     * @throws IOException if traversal fails
     */
    public static List<Path> listFilesRecursively(Path dir) throws IOException {
        Objects.requireNonNull(dir, "dir must not be null");
        List<Path> list = new ArrayList<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                list.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        return list;
    }

    /**
     * Finds files with a given extension under a directory tree.
     *
     * @param dir directory; must not be null
     * @param ext extension without dot; must not be null
     * @return matching files list
     * @throws IOException if traversal fails
     */
    public static List<Path> findFilesByExtension(Path dir, String ext) throws IOException {
        Objects.requireNonNull(dir, "dir must not be null");
        Objects.requireNonNull(ext, "ext must not be null");
        List<Path> result = new ArrayList<>();
        String normalizedExt = ext.startsWith(".") ? ext.substring(1) : ext;
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (getExtension(file).equalsIgnoreCase(normalizedExt)) {
                    result.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }

    /**
     * Copies a file with optional overwrite.
     *
     * @param src       source; must not be null
     * @param dest      destination; must not be null
     * @param overwrite replace if exists
     * @throws IOException if copy fails
     */
    public static void copyFile(Path src, Path dest, boolean overwrite) throws IOException {
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(dest, "dest must not be null");
        CopyOption[] opts = overwrite ? new CopyOption[]{StandardCopyOption.REPLACE_EXISTING} : new CopyOption[]{};
        Files.copy(src, dest, opts);
    }

    /**
     * Recursively copies a directory tree.
     *
     * @param src       source; must not be null
     * @param dest      destination; must not be null
     * @param overwrite replace if exists
     * @throws IOException if copy fails
     */
    public static void copyDirectory(Path src, Path dest, boolean overwrite) throws IOException {
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(dest, "dest must not be null");
        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path target = dest.resolve(src.relativize(dir));
                ensureDirectoryExists(target);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = dest.resolve(src.relativize(file));
                copyFile(file, targetFile, overwrite);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Moves or renames a file/directory, optionally atomically.
     *
     * @param src    source; must not be null
     * @param target destination; must not be null
     * @param atomic atomic move if true
     * @throws IOException if move fails
     */
    public static void move(Path src, Path target, boolean atomic) throws IOException {
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(target, "target must not be null");
        CopyOption[] opts = atomic ? new CopyOption[]{StandardCopyOption.ATOMIC_MOVE} : new CopyOption[]{};
        Files.move(src, target, opts);
    }

    /**
     * Computes a message digest (e.g., MD5, SHA-1, SHA-256) for a file and returns it as a hex string.
     *
     * @param file      the file path; must not be null
     * @param algorithm the digest algorithm name; must not be null (e.g., "MD5", "SHA-256")
     * @return lowercase hex-encoded digest
     * @throws IOException              if an I/O error occurs
     * @throws NoSuchAlgorithmException if the algorithm is not available
     */
    public static String computeChecksum(Path file, String algorithm) throws IOException, NoSuchAlgorithmException {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream is = Files.newInputStream(file); DigestInputStream dis = new DigestInputStream(is, digest)) {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) {
                // digest updated by stream
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ------------ 7. ZIP Archiving ------------

    /**
     * Creates a ZIP archive of the specified directory, including all nested files.
     *
     * @param srcDir  the source directory; must not be null
     * @param zipFile the output ZIP file path; must not be null
     * @throws IOException if an I/O error occurs during compression
     */
    public static void zipDirectory(Path srcDir, Path zipFile) throws IOException {
        Objects.requireNonNull(srcDir, "srcDir must not be null");
        Objects.requireNonNull(zipFile, "zipFile must not be null");
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            Files.walk(srcDir).filter(path -> !Files.isDirectory(path)).forEach(path -> {
                ZipEntry entry = new ZipEntry(srcDir.relativize(path).toString());
                try {
                    zs.putNextEntry(entry);
                    Files.copy(path, zs);
                    zs.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    /**
     * Extracts a ZIP archive into the specified target directory.
     *
     * @param zipFile   the ZIP file path; must not be null
     * @param targetDir the destination directory; must not be null
     * @throws IOException if an I/O error occurs during extraction
     */
    public static void unzip(Path zipFile, Path targetDir) throws IOException {
        Objects.requireNonNull(zipFile, "zipFile must not be null");
        Objects.requireNonNull(targetDir, "targetDir must not be null");
        ensureDirectoryExists(targetDir);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = targetDir.resolve(entry.getName()).normalize();
                if (entry.isDirectory()) {
                    ensureDirectoryExists(newPath);
                } else {
                    ensureDirectoryExists(newPath.getParent());
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Creates and registers a WatchService for the specified events on the given directory.
     * The caller is responsible for polling or taking keys from the returned WatchService,
     * resetting keys after processing, and closing the service when no longer needed.
     *
     * @param dir    the directory to watch (must exist and be a directory)
     * @param events the kinds of events to monitor (e.g., ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
     * @return the initialized WatchService, ready to poll for events
     * @throws IOException if an I/O error occurs opening the watch service or registering the directory
     */
    public static WatchService watchDirectory(Path dir, WatchEvent.Kind<?>... events) throws IOException {
        WatchService watcher = FileSystems.getDefault().newWatchService();
        dir.register(watcher, events);
        return watcher;
    }

}