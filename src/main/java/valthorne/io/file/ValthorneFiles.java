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

    private ValthorneFiles() {
        // utility class
    }

    /**
     * Reads a classpath resource into a byte[].
     *
     * @param resourcePath path relative to classpath root (e.g. "data/test.json")
     */
    public static byte[] readBytes(String resourcePath) {
        try (InputStream in = openResource(resourcePath)) {
            return in.readAllBytes(); // Java 9+
        } catch (IOException e) {
            throw new ValthorneFileException("Failed to read resource bytes: " + resourcePath, e);
        }
    }

    /**
     * Reads a classpath resource into a UTF-8 String.
     *
     * @param resourcePath path relative to classpath root (e.g. "data/test.json")
     */
    public static String readString(String resourcePath) {
        return readString(resourcePath, StandardCharsets.UTF_8);
    }

    /**
     * Reads a classpath resource into a String using the given charset.
     *
     * @param resourcePath path relative to classpath root (e.g. "data/test.json")
     * @param charset      character set to decode bytes
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
     * Returns true if the given classpath resource exists.
     *
     * @param resourcePath path relative to classpath root (e.g. "data/test.json")
     */
    public static boolean exists(String resourcePath) {
        String normalized = normalize(resourcePath);
        return ValthorneFiles.class.getClassLoader().getResource(normalized) != null;
    }

    /**
     * Opens a classpath resource as an InputStream.
     * Public in case you later decide to support streams, but everything else works without requiring streams.
     */
    public static InputStream openResource(String resourcePath) {
        String normalized = normalize(resourcePath);
        InputStream in = ValthorneFiles.class.getClassLoader().getResourceAsStream(normalized);
        if (in == null) {
            throw new ValthorneFileNotFoundException("Resource not found on classpath: " + normalized);
        }
        return in;
    }

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