package valthorne.io.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Utility class for handling file operations related to classpath resources.
 * Provides methods for reading, checking existence, and extracting files
 * from the classpath into usable formats or temporary files.
 * <p>
 * All methods are static and primarily handle InputStream operations,
 * byte array conversions, String encoding, and temporary file creation.
 *
 * @author Albert Beaupre
 * @since February 28th, 2026
 */
public final class ValthorneFiles {

    /**
     * Prevents construction of this stateless classpath resource utility. All resource
     * lookup and extraction operations are exposed through static methods.
     */
    private ValthorneFiles() {
        // utility class
    }

    /**
     * Reads the complete classpath resource into memory and closes its stream.
     * The returned array is independent of the stream and can be modified by the caller.
     * No size limit is imposed on resource contents.
     *
     * @param resourcePath resource name relative to the classpath root
     * @return newly allocated resource bytes
     * @throws ValthorneFileException if reading fails or the resource cannot be found
     */
    public static byte[] readBytes(String resourcePath) {
        try (InputStream in = openResource(resourcePath)) {
            return in.readAllBytes(); // Java 9+
        } catch (IOException e) {
            throw new ValthorneFileException("Failed to read resource bytes: " + resourcePath, e);
        }
    }

    /**
     * Reads the entire resource as UTF-8 text and closes its stream. Decoding follows
     * String's replacement behavior for malformed byte sequences.
     *
     * @param resourcePath resource name relative to the classpath root
     * @return decoded resource contents
     * @throws ValthorneFileException if reading fails or the resource cannot be found
     */
    public static String readString(String resourcePath) {
        return readString(resourcePath, StandardCharsets.UTF_8);
    }

    /**
     * Reads all resource bytes and decodes them with the supplied character set.
     * The source stream is closed by readBytes before decoding, and malformed byte
     * sequences follow String's replacement behavior.
     *
     * @param resourcePath resource name relative to the classpath root
     * @param charset character set for decoding
     * @return decoded resource contents
     * @throws NullPointerException if charset is null
     * @throws ValthorneFileException if reading fails or the resource cannot be found
     */
    public static String readString(String resourcePath, Charset charset) {
        byte[] bytes = readBytes(resourcePath);
        return new String(bytes, charset);
    }

    /**
     * Extracts a classpath resource to a temp file and returns its absolute filesystem path.
     * Use this if an API only accepts a String path and cannot read from streams/bytes directly.
     * <p>
     * The temp file is marked deleteOnExit().
     *
     * @param resourcePath path relative to classpath root (e.g. "data/test.json")
     * @return absolute filesystem path to the extracted temp file
     */
    public static String extractToTempPath(String resourcePath) {
        Path path = extractToTempFile(resourcePath);
        return path.toAbsolutePath().toString();
    }

    /**
     * Extracts a classpath resource to a temp file and returns the Path.
     * The temp file is marked deleteOnExit().
     *
     * @param resourcePath path relative to classpath root (e.g. "data/test.json")
     */
    public static Path extractToTempFile(String resourcePath) {
        String normalized = normalize(resourcePath);

        String fileName = Path.of(normalized).getFileName().toString();
        String prefix = "valthorne-";
        String suffix = "-" + (fileName.isBlank() ? "resource" : fileName);

        Path temp;
        try {
            temp = Files.createTempFile(prefix, suffix);
            temp.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new ValthorneFileException("Failed to create temp file for resource: " + resourcePath, e);
        }

        try (InputStream in = openResource(normalized)) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            return temp;
        } catch (IOException e) {
            throw new ValthorneFileException("Failed to extract resource to temp file: " + resourcePath, e);
        }
    }

    /**
     * Checks for a resource using this class's defining class loader without opening
     * its stream. A positive result indicates lookup success at this instant and does
     * not guarantee that a later read will succeed.
     *
     * @param resourcePath resource name relative to the classpath root
     * @return whether class-loader lookup returns a resource URL
     * @throws IllegalArgumentException if the resource name is null or empty
     */
    public static boolean exists(String resourcePath) {
        String normalized = normalize(resourcePath);
        return ValthorneFiles.class.getClassLoader().getResource(normalized) != null;
    }

    /**
     * Opens a classpath resource with this class's defining class loader.
     * The returned stream belongs to the caller and must be closed, preferably using
     * try-with-resources. Leading slashes and surrounding whitespace are normalized.
     *
     * @param resourcePath resource name relative to the classpath root
     * @return newly opened resource stream
     * @throws IllegalArgumentException if the resource name is null or empty
     * @throws ValthorneFileNotFoundException if lookup finds no resource
     */
    public static InputStream openResource(String resourcePath) {
        String normalized = normalize(resourcePath);
        InputStream in = ValthorneFiles.class.getClassLoader().getResourceAsStream(normalized);
        if (in == null) {
            throw new ValthorneFileNotFoundException("Resource not found on classpath: " + normalized);
        }
        return in;
    }

    /**
     * Trims surrounding whitespace and removes every leading slash for ClassLoader
     * lookup. Does not rewrite backslashes, collapse dot segments, or resolve filesystem
     * paths; the remaining text is used as a classpath resource name.
     *
     * @param resourcePath requested resource name
     * @return nonempty resource name without leading slashes
     * @throws IllegalArgumentException if the argument is null or normalizes to empty
     */
    private static String normalize(String resourcePath) {
        if (resourcePath == null) {
            throw new IllegalArgumentException("resourcePath cannot be null");
        }
        String p = resourcePath.trim();
        // ClassLoader resources should not start with '/'
        while (p.startsWith("/")) p = p.substring(1);
        if (p.isEmpty()) {
            throw new IllegalArgumentException("resourcePath cannot be empty");
        }
        return p;
    }
}