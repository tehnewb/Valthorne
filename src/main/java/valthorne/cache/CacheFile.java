package valthorne.cache;

/**
 * A single named file entry inside a {@link CacheArchive}.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * CacheFile file = new CacheFile("player.png", playerPngBytes);
 * String name = file.getName();
 * byte[] data = file.getData();
 *
 * file.setName("player_idle.png");
 * file.setData(otherBytes);
 * }</pre>
 *
 * <h2>Notes</h2>
 * <p>
 * This class is intentionally minimal and acts as a simple data holder for file name + bytes.
 * Higher-level logic (compression, indexing, etc.) is handled by {@link CacheArchive} and {@link CacheStore}.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 3rd, 2026
 */
public class CacheFile {

    private String name; // File name stored in the archive payload.
    private byte[] data; // Raw file bytes stored in the archive payload.

    /**
     * Creates an empty file entry.
     *
     * <p>
     * This is useful for serializers or frameworks that require a no-arg constructor.
     * </p>
     */
    public CacheFile() {}

    /**
     * Creates a file entry with name and data.
     *
     * <p>
     * This stores references as-is. If you need defensive copies, copy the array before passing it in.
     * </p>
     *
     * @param name file name
     * @param data file bytes
     */
    public CacheFile(String name, byte[] data) {
        this.name = name;
        this.data = data;
    }

    /**
     * Returns the file name.
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the file name.
     *
     * @param name new name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the file data bytes.
     *
     * <p>
     * This returns the internal array reference. Copy it if you need immutability.
     * </p>
     *
     * @return data bytes
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Sets the file data bytes.
     *
     * <p>
     * This stores the reference as-is. Copy the array if you need defensive storage.
     * </p>
     *
     * @param data new data bytes
     */
    public void setData(byte[] data) {
        this.data = data;
    }
}