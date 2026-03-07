package valthorne.cache.checksum;

/**
 * Represents a checksum row for a single {@code CacheFile}.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * FileRow row = new FileRow("player.png", 0xDEADBEEF, 1024);
 * System.out.println(row.name());
 * System.out.println(row.fileLength());
 * }</pre>
 *
 * <h2>What this record does</h2>
 * <p>
 * {@code FileRow} stores a file name plus CRC32 and length for the file's raw data.
 * It is typically used for debugging, diagnostics, or deeper diffing when archive-level
 * checks are not enough.
 * </p>
 *
 * @param name       file name (null becomes "")
 * @param fileCrc32  CRC32 of the raw file bytes
 * @param fileLength length of the raw file bytes
 * @author Albert Beaupre
 * @since March 5th, 2026
 */
public record FileRow(String name, int fileCrc32, int fileLength) {

    /**
     * Shared empty file row array to avoid allocations.
     */
    public static final FileRow[] EMPTY = new FileRow[0];

    /**
     * Canonical constructor with null-safe normalization for name.
     *
     * <p>
     * Ensures {@code name} is never null so consumers can safely call {@link #name()}.
     * </p>
     *
     * @param name       file name (null becomes "")
     * @param fileCrc32  CRC32 of the raw file bytes
     * @param fileLength length of the raw file bytes
     */
    public FileRow(String name, int fileCrc32, int fileLength) {
        this.name = name == null ? "" : name;
        this.fileCrc32 = fileCrc32;
        this.fileLength = fileLength;
    }
}