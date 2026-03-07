package valthorne.cache.checksum;

/**
 * Represents a checksum row for a single {@code CacheArchive}.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Build a row (usually created by ChecksumTable.build()).
 * FileRow[] files = FileRow.EMPTY;
 * ArchiveRow row = new ArchiveRow("textures", (byte) 1, 0x12345678, 4096, files);
 *
 * // Read fields directly (records expose accessor methods).
 * System.out.println(row.name());
 * System.out.println(row.archiveCrc32());
 * }</pre>
 *
 * <h2>What this record does</h2>
 * <p>
 * {@code ArchiveRow} is a compact container for archive-level integrity metadata:
 * name, compression ID, CRC32, length, and optional per-file rows.
 * </p>
 *
 * @param name          archive name (null becomes "")
 * @param compression   compression ID used to produce the served bytes
 * @param archiveCrc32  CRC32 of the served archive bytes
 * @param archiveLength length of the served archive bytes
 * @param files         optional per-file rows (null becomes {@link FileRow#EMPTY})
 * @author Albert Beaupre
 * @since March 5th, 2026
 */
public record ArchiveRow(String name, byte compression, int archiveCrc32, int archiveLength, FileRow[] files) {

    /**
     * Canonical constructor with null-safe normalization for name and files.
     *
     * <p>
     * Records allow you to validate/normalize inputs inside the canonical constructor.
     * This implementation ensures that {@code name} is never null and {@code files} is never null.
     * </p>
     *
     * @param name          archive name (null becomes "")
     * @param compression   compression ID used to produce the served bytes
     * @param archiveCrc32  CRC32 of the served archive bytes
     * @param archiveLength length of the served archive bytes
     * @param files         per-file rows (null becomes {@link FileRow#EMPTY})
     */
    public ArchiveRow(String name, byte compression, int archiveCrc32, int archiveLength, FileRow[] files) {
        this.name = name == null ? "" : name;
        this.compression = compression;
        this.archiveCrc32 = archiveCrc32;
        this.archiveLength = archiveLength;
        this.files = files == null ? FileRow.EMPTY : files;
    }
}