package valthorne.utility;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.*;
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
 *
 * <p>
 * {@code FileUtility} is a focused collection of static helpers for common file-system tasks that
 * tend to show up repeatedly in tools, launchers, editors, asset pipelines, cache systems,
 * packagers, and engine-side utilities. The class is intentionally centered around operations that
 * are usually more verbose when written directly with the standard JDK APIs, such as recursive
 * traversal, safe ZIP extraction, checksum generation, extension manipulation, and directory tree
 * copying or deletion.
 * </p>
 *
 * <p>
 * This utility does <b>not</b> try to replace {@link Files}, {@link Path}, or other core NIO APIs.
 * Instead, it complements them. For simple direct tasks such as reading bytes, writing strings,
 * checking existence, or querying size and timestamps, the normal JDK APIs are still the best
 * choice. This class is meant for the slightly more structured and repeatable cases where you want
 * a single reusable method instead of rewriting the same traversal or archive logic each time.
 * </p>
 *
 * <h2>What this class is good for</h2>
 * <ul>
 *     <li>Extracting or replacing file extensions</li>
 *     <li>Creating directories on demand</li>
 *     <li>Recursively deleting or copying directory trees</li>
 *     <li>Listing files recursively or filtering by extension</li>
 *     <li>Computing stable checksums such as SHA-256 for caching or validation</li>
 *     <li>Creating ZIP archives and extracting them safely</li>
 *     <li>Creating a {@link WatchService} for directory monitoring</li>
 * </ul>
 *
 * <h2>Design notes</h2>
 * <ul>
 *     <li>This is a pure static utility class and cannot be instantiated.</li>
 *     <li>Methods validate important inputs with {@link Objects#requireNonNull(Object, String)} where appropriate.</li>
 *     <li>Recursive directory operations use {@link Files#walkFileTree(Path, java.nio.file.FileVisitor)} for reliability.</li>
 *     <li>ZIP extraction includes a path-normalization safety check to prevent ZIP Slip attacks.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Path assets = Paths.get("assets");
 * Path backupZip = Paths.get("backup/assets.zip");
 * Path extracted = Paths.get("temp/extracted-assets");
 *
 * // Ensure destination directory exists.
 * FileUtility.ensureDirectoryExists(backupZip.getParent());
 *
 * // Create an archive.
 * FileUtility.zipDirectory(assets, backupZip);
 *
 * // Extract it safely.
 * FileUtility.unzip(backupZip, extracted);
 *
 * // Find all PNG files.
 * List<Path> pngFiles = FileUtility.findFilesByExtension(extracted, "png");
 *
 * // Compute a checksum for one of them.
 * if (!pngFiles.isEmpty()) {
 *     String sha256 = FileUtility.computeChecksum(pngFiles.get(0), "SHA-256");
 *     System.out.println("Checksum: " + sha256);
 * }
 *
 * // Copy the extracted directory somewhere else.
 * FileUtility.copyDirectory(extracted, Paths.get("build/copied-assets"), true);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public final class FileUtility {

    /**
     * Creates a non-instantiable utility type.
     *
     * <p>
     * This constructor always throws because the class is intended to be used only through its
     * static methods.
     * </p>
     */
    private FileUtility() {
        throw new UnsupportedOperationException("FileUtility cannot be instantiated");
    }

    /**
     * Returns the extension of a file name without the leading dot.
     *
     * <p>
     * This method works on the provided string exactly as given. It does not require the input to
     * be a real file on disk. The extension is determined from the last dot in the name.
     * </p>
     *
     * <p>
     * The method returns an empty string when:
     * </p>
     * <ul>
     *     <li>The input is {@code null}</li>
     *     <li>The input is empty</li>
     *     <li>No dot is present</li>
     *     <li>The dot is the first character, such as {@code ".gitignore"}</li>
     *     <li>The dot is the last character</li>
     * </ul>
     *
     * @param fileName the raw file name or path string
     * @return the extension without the dot, or an empty string when no usable extension exists
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
     * Returns the extension of a {@link File}'s name without the leading dot.
     *
     * <p>
     * This overload delegates to {@link #getExtension(String)} using {@link File#getName()}.
     * Only the file name portion is examined.
     * </p>
     *
     * @param file the file whose name should be inspected
     * @return the extension without the dot, or an empty string when no usable extension exists
     * @throws NullPointerException if {@code file} is null
     */
    public static String getExtension(File file) {
        Objects.requireNonNull(file, "file must not be null");
        return getExtension(file.getName());
    }

    /**
     * Returns the extension of a {@link Path}'s file name without the leading dot.
     *
     * <p>
     * This overload uses {@link Path#getFileName()} and delegates to {@link #getExtension(String)}.
     * Only the last name element is inspected.
     * </p>
     *
     * @param path the path whose file name should be inspected
     * @return the extension without the dot, or an empty string when no usable extension exists
     * @throws NullPointerException if {@code path} is null
     */
    public static String getExtension(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        Path fileName = path.getFileName();
        return fileName == null ? "" : getExtension(fileName.toString());
    }

    /**
     * Returns the base name of a file name using the last dot as the extension separator.
     *
     * <p>
     * For example, {@code "archive.tar.gz"} becomes {@code "archive.tar"}.
     * If no dot exists, the input is returned unchanged.
     * </p>
     *
     * @param fileName the file name to inspect
     * @return the name up to the last dot, the original value when no dot exists, or {@code null} when input is null
     */
    public static String getBaseName(String fileName) {
        if (fileName == null) {
            return null;
        }

        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex == -1 ? fileName : fileName.substring(0, dotIndex);
    }

    /**
     * Removes everything after the first dot in a file name.
     *
     * <p>
     * This differs from {@link #getBaseName(String)}. It removes <b>all</b> extension-like suffixes
     * after the first dot. For example, {@code "archive.tar.gz"} becomes {@code "archive"}.
     * </p>
     *
     * @param fileName the file name to inspect
     * @return the name before the first dot, the original value when no dot exists, or {@code null} when input is null
     */
    public static String removeExtension(String fileName) {
        if (fileName == null) {
            return null;
        }

        int dotIndex = fileName.indexOf('.');
        return dotIndex == -1 ? fileName : fileName.substring(0, dotIndex);
    }

    /**
     * Replaces the extension of a file name.
     *
     * <p>
     * The replacement extension may be provided with or without a leading dot. If the new
     * extension is {@code null} or empty, the returned value is simply the base name produced by
     * {@link #getBaseName(String)}.
     * </p>
     *
     * @param fileName the original file name
     * @param newExt   the new extension, with or without a leading dot
     * @return the updated file name, or {@code null} when {@code fileName} is null
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
     * Returns the parent path string of a raw file path.
     *
     * <p>
     * If the provided path has no parent, an empty string is returned. If the input itself is
     * {@code null}, this method returns {@code null}.
     * </p>
     *
     * @param filePath the raw file path string
     * @return the parent path, an empty string when no parent exists, or {@code null} when input is null
     */
    public static String getParentPath(String filePath) {
        if (filePath == null) {
            return null;
        }

        Path parent = Paths.get(filePath).getParent();
        return parent == null ? "" : parent.toString();
    }

    /**
     * Ensures that a directory exists, creating it and all missing parents when necessary.
     *
     * <p>
     * If the directory already exists, this method does nothing. This method is ideal before file
     * creation, ZIP extraction, tree copying, or any staged build output.
     * </p>
     *
     * @param dir the directory to create if needed
     * @throws IOException          if the directory cannot be created
     * @throws NullPointerException if {@code dir} is null
     */
    public static void ensureDirectoryExists(Path dir) throws IOException {
        Objects.requireNonNull(dir, "dir must not be null");

        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    /**
     * Deletes a file or directory quietly.
     *
     * <p>
     * This method attempts to delete the given path using {@link Files#deleteIfExists(Path)}.
     * It returns {@code true} when the path was deleted successfully or when it did not exist.
     * It returns {@code false} when an {@link IOException} occurs.
     * </p>
     *
     * <p>
     * This is intended for best-effort cleanup operations where failure should not abort the caller.
     * It does not delete directory contents recursively.
     * </p>
     *
     * @param path the path to delete
     * @return true when the path was deleted or did not exist, false when deletion failed
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
     * Recursively deletes an entire directory tree or a single file.
     *
     * <p>
     * If the path does not exist, this method simply returns. For a directory, every nested file
     * is deleted first, followed by each directory on the way back out.
     * </p>
     *
     * @param path the root path to delete
     * @throws IOException          if any delete operation fails
     * @throws NullPointerException if {@code path} is null
     */
    public static void deleteRecursively(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");

        if (Files.notExists(path)) {
            return;
        }

        Files.walkFileTree(path, new SimpleFileVisitor<>() {

            /**
             * Deletes each visited file during the traversal.
             *
             * @param file the file being visited
             * @param attrs the file's attributes
             * @return continue walking after deletion
             * @throws IOException if the file cannot be deleted
             */
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            /**
             * Deletes each directory after all children were already removed.
             *
             * @param dir the directory that has just finished traversal
             * @param exc an I/O exception raised during traversal, if any
             * @return continue walking after deletion
             * @throws IOException if the directory cannot be deleted
             */
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }

                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Lists only the regular files directly inside a directory.
     *
     * <p>
     * This method is non-recursive. Subdirectories are ignored. The returned list is newly created
     * and may be empty when no files are present.
     * </p>
     *
     * @param dir the directory to inspect
     * @return a list containing the regular files directly under the directory
     * @throws IOException          if the directory cannot be read
     * @throws NullPointerException if {@code dir} is null
     */
    public static List<Path> listFiles(Path dir) throws IOException {
        Objects.requireNonNull(dir, "dir must not be null");

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            List<Path> list = new ArrayList<>();

            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    list.add(path);
                }
            }

            return list;
        }
    }

    /**
     * Recursively lists every regular file under a directory tree.
     *
     * <p>
     * The returned list contains files only. Directories themselves are not included.
     * </p>
     *
     * @param dir the root directory to traverse
     * @return a list of all regular files found in the tree
     * @throws IOException          if traversal fails
     * @throws NullPointerException if {@code dir} is null
     */
    public static List<Path> listFilesRecursively(Path dir) throws IOException {
        Objects.requireNonNull(dir, "dir must not be null");

        List<Path> list = new ArrayList<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {

            /**
             * Adds each visited regular file to the result list.
             *
             * @param file the visited file
             * @param attrs the file's attributes
             * @return continue traversal
             */
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                list.add(file);
                return FileVisitResult.CONTINUE;
            }
        });

        return list;
    }

    /**
     * Recursively finds files whose extension matches the requested value.
     *
     * <p>
     * Matching is case-insensitive. The requested extension may be provided with or without a
     * leading dot.
     * </p>
     *
     * @param dir the root directory to traverse
     * @param ext the extension to match, with or without a leading dot
     * @return a list of matching files
     * @throws IOException          if traversal fails
     * @throws NullPointerException if {@code dir} or {@code ext} is null
     */
    public static List<Path> findFilesByExtension(Path dir, String ext) throws IOException {
        Objects.requireNonNull(dir, "dir must not be null");
        Objects.requireNonNull(ext, "ext must not be null");

        List<Path> result = new ArrayList<>();
        String normalizedExt = ext.startsWith(".") ? ext.substring(1) : ext;

        Files.walkFileTree(dir, new SimpleFileVisitor<>() {

            /**
             * Adds each file whose extension matches the requested extension.
             *
             * @param file the visited file
             * @param attrs the file's attributes
             * @return continue traversal
             */
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
     * Copies a single file to a destination path.
     *
     * <p>
     * When {@code overwrite} is true, an existing destination file is replaced. When it is false,
     * the copy fails if the destination already exists.
     * </p>
     *
     * @param src       the source file
     * @param dest      the destination file
     * @param overwrite true to replace an existing destination file
     * @throws IOException          if the copy fails
     * @throws NullPointerException if {@code src} or {@code dest} is null
     */
    public static void copyFile(Path src, Path dest, boolean overwrite) throws IOException {
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(dest, "dest must not be null");

        CopyOption[] options = overwrite ? new CopyOption[]{StandardCopyOption.REPLACE_EXISTING} : new CopyOption[0];

        Files.copy(src, dest, options);
    }

    /**
     * Recursively copies an entire directory tree to a destination directory.
     *
     * <p>
     * Directories are created as needed before nested files are copied. Destination file overwrite
     * behavior is controlled by the {@code overwrite} flag.
     * </p>
     *
     * @param src       the source root directory
     * @param dest      the destination root directory
     * @param overwrite true to replace existing files
     * @throws IOException          if any part of the copy fails
     * @throws NullPointerException if {@code src} or {@code dest} is null
     */
    public static void copyDirectory(Path src, Path dest, boolean overwrite) throws IOException {
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(dest, "dest must not be null");

        Files.walkFileTree(src, new SimpleFileVisitor<>() {

            /**
             * Creates each destination directory before its files are copied.
             *
             * @param dir the source directory currently being entered
             * @param attrs the directory's attributes
             * @return continue traversal
             * @throws IOException if the destination directory cannot be created
             */
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path target = dest.resolve(src.relativize(dir));
                ensureDirectoryExists(target);
                return FileVisitResult.CONTINUE;
            }

            /**
             * Copies each visited file to the matching relative destination path.
             *
             * @param file the source file
             * @param attrs the file's attributes
             * @return continue traversal
             * @throws IOException if the file cannot be copied
             */
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = dest.resolve(src.relativize(file));
                copyFile(file, targetFile, overwrite);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Moves or renames a file or directory.
     *
     * <p>
     * When {@code atomic} is true, the method requests an atomic move through
     * {@link StandardCopyOption#ATOMIC_MOVE}. Whether that is supported depends on the file system.
     * </p>
     *
     * @param src    the source path
     * @param target the destination path
     * @param atomic true to request an atomic move
     * @throws IOException          if the move fails
     * @throws NullPointerException if {@code src} or {@code target} is null
     */
    public static void move(Path src, Path target, boolean atomic) throws IOException {
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(target, "target must not be null");

        CopyOption[] options = atomic ? new CopyOption[]{StandardCopyOption.ATOMIC_MOVE} : new CopyOption[0];

        Files.move(src, target, options);
    }

    /**
     * Computes a message-digest checksum for a file and returns it as lowercase hexadecimal text.
     *
     * <p>
     * The file is streamed through a {@link DigestInputStream}, so the full file does not need to
     * be loaded into memory at once. This makes the method suitable for larger files.
     * </p>
     *
     * <p>
     * Common algorithm values include:
     * </p>
     * <ul>
     *     <li>{@code "MD5"}</li>
     *     <li>{@code "SHA-1"}</li>
     *     <li>{@code "SHA-256"}</li>
     *     <li>{@code "SHA-512"}</li>
     * </ul>
     *
     * @param file      the file whose checksum should be computed
     * @param algorithm the digest algorithm name
     * @return the checksum as lowercase hexadecimal text
     * @throws IOException              if the file cannot be read
     * @throws NoSuchAlgorithmException if the requested digest algorithm is not available
     * @throws NullPointerException     if {@code file} or {@code algorithm} is null
     */
    public static String computeChecksum(Path file, String algorithm) throws IOException, NoSuchAlgorithmException {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(algorithm, "algorithm must not be null");

        MessageDigest digest = MessageDigest.getInstance(algorithm);

        try (InputStream inputStream = Files.newInputStream(file); DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
            byte[] buffer = new byte[8192];
            while (digestInputStream.read(buffer) != -1) {
            }
        }

        byte[] hash = digest.digest();
        StringBuilder builder = new StringBuilder(hash.length * 2);

        for (byte value : hash) {
            builder.append(String.format("%02x", value));
        }

        return builder.toString();
    }

    /**
     * Creates a ZIP archive from the full contents of a directory tree.
     *
     * <p>
     * Only regular files are written as ZIP entries. Each entry is stored using a path relative to
     * the source directory root, which preserves the directory structure inside the archive.
     * </p>
     *
     * @param srcDir  the source directory to archive
     * @param zipFile the destination ZIP file path
     * @throws IOException          if archive creation fails
     * @throws NullPointerException if {@code srcDir} or {@code zipFile} is null
     */
    public static void zipDirectory(Path srcDir, Path zipFile) throws IOException {
        Objects.requireNonNull(srcDir, "srcDir must not be null");
        Objects.requireNonNull(zipFile, "zipFile must not be null");

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            Files.walk(srcDir).filter(path -> !Files.isDirectory(path)).forEach(path -> {
                ZipEntry entry = new ZipEntry(srcDir.relativize(path).toString());
                try {
                    zipOutputStream.putNextEntry(entry);
                    Files.copy(path, zipOutputStream);
                    zipOutputStream.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Extracts a ZIP archive into a target directory.
     *
     * <p>
     * This method creates directories as needed and replaces existing files when a ZIP entry
     * targets the same path.
     * </p>
     *
     * <p>
     * For safety, each extracted path is normalized and verified to remain inside the requested
     * destination directory. This prevents ZIP Slip path traversal issues.
     * </p>
     *
     * @param zipFile   the ZIP archive to extract
     * @param targetDir the destination directory
     * @throws IOException          if extraction fails
     * @throws NullPointerException if {@code zipFile} or {@code targetDir} is null
     */
    public static void unzip(Path zipFile, Path targetDir) throws IOException {
        Objects.requireNonNull(zipFile, "zipFile must not be null");
        Objects.requireNonNull(targetDir, "targetDir must not be null");

        ensureDirectoryExists(targetDir);
        Path normalizedTargetDir = targetDir.toAbsolutePath().normalize();

        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path resolved = normalizedTargetDir.resolve(entry.getName()).normalize();

                if (!resolved.startsWith(normalizedTargetDir)) {
                    throw new IOException("ZIP entry escapes target directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    ensureDirectoryExists(resolved);
                } else {
                    Path parent = resolved.getParent();
                    if (parent != null) {
                        ensureDirectoryExists(parent);
                    }
                    Files.copy(zipInputStream, resolved, StandardCopyOption.REPLACE_EXISTING);
                }

                zipInputStream.closeEntry();
            }
        }
    }

    /**
     * Creates and registers a {@link WatchService} for a directory.
     *
     * <p>
     * The returned watcher is already registered for the provided event kinds. The caller is
     * responsible for:
     * </p>
     * <ul>
     *     <li>Polling or blocking on the watcher</li>
     *     <li>Resetting received keys after processing</li>
     *     <li>Closing the watcher when finished</li>
     * </ul>
     *
     * @param dir    the directory to watch
     * @param events the event kinds to register, such as create, delete, or modify
     * @return a ready-to-use watch service
     * @throws IOException          if the watch service cannot be created or registered
     * @throws NullPointerException if {@code dir} is null
     */
    public static WatchService watchDirectory(Path dir, WatchEvent.Kind<?>... events) throws IOException {
        Objects.requireNonNull(dir, "dir must not be null");

        WatchService watcher = FileSystems.getDefault().newWatchService();
        dir.register(watcher, events);
        return watcher;
    }
}