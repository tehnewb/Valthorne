package valthorne.cache.checksum;

import valthorne.cache.CacheArchive;
import valthorne.cache.CacheFile;
import valthorne.cache.CacheStore;
import valthorne.io.buffer.DynamicByteBuffer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * Builds and verifies a CRC32-based checksum table for a {@link CacheStore}.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Build a cache store (or load one).
 * CacheStore store = CacheStore.load(Path.of("cache.dat"));
 *
 * // Build a checksum table for the current store (include file rows for per-file validation).
 * ChecksumTable table = ChecksumTable.build(store, true);
 *
 * // Encode to bytes (store alongside your cache file).
 * byte[] encoded = ChecksumTable.encode(table);
 * Files.write(Path.of("cache.chk"), encoded);
 *
 * // Later: load and verify the checksum table bytes.
 * byte[] chkBytes = Files.readAllBytes(Path.of("cache.chk"));
 * ChecksumTable loaded = ChecksumTable.decode(chkBytes); // throws if CRC mismatches
 *
 * // Compare two tables to find which archives changed.
 * ChecksumTable newer = ChecksumTable.build(store, false); // maybe you don't need file rows
 * String[] changed = newer.diffChangedArchives(loaded);
 *
 * // Example: if changed contains "textures", re-download or rebuild that archive.
 * for (String name : changed) {
 *     System.out.println("Changed archive: " + name);
 * }
 * }</pre>
 *
 * <h2>What this class does</h2>
 * <p>
 * {@code ChecksumTable} creates a compact, serializable snapshot of the integrity-relevant properties
 * of a {@link CacheStore}. For each {@link CacheArchive}, it records:
 * </p>
 * <ul>
 *     <li>Archive name</li>
 *     <li>Compression ID</li>
 *     <li>CRC32 of the <b>served bytes</b> (the compressed archive payload produced by {@link CacheArchive#compress(CacheArchive)})</li>
 *     <li>Length of those served bytes</li>
 * </ul>
 *
 * <p>
 * Optionally, it can also store per-file CRC32 and length for each {@link CacheFile} inside an archive.
 * This helps you detect which file(s) changed inside an archive when you already have the decompressed file data.
 * </p>
 *
 * <h2>Encoding format</h2>
 * <p>
 * The encoded output is:
 * </p>
 * <pre>{@code
 * [u16 storeVersion]
 * [i32 payloadCrc32]   // CRC32 of the payload bytes that follow
 * [payload...]
 * }</pre>
 *
 * <p>
 * The payload itself contains a u16 archive count, then for each archive:
 * </p>
 * <pre>{@code
 * [u8 nameLen][name UTF-8 bytes...]
 * [u8 compression]
 * [i32 archiveCrc32]
 * [i32 archiveLength]
 * [u16 fileCount]
 *   repeated fileCount times:
 *     [u8 nameLen][name UTF-8 bytes...]
 *     [i32 fileCrc32]
 *     [i32 fileLength]
 * }</pre>
 *
 * <p>
 * Names are stored with a 1-byte length prefix (0..255). Longer names throw an exception when encoding.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 5th, 2026
 */
public final class ChecksumTable {

    /**
     * Maximum UTF-8 byte length for any encoded name.
     */
    private static final int MAX_NAME_BYTES = 255;

    /**
     * CacheStore version this table was built from.
     */
    private final int storeVersion;

    /**
     * CRC32 of the encoded payload (everything after this field).
     */
    private final int tableCrc32;

    /**
     * Archive checksum rows in the order they were built/decoded.
     */
    private final ArchiveRow[] archives;

    /**
     * Creates a checksum table instance.
     *
     * <p>
     * This is private because callers should use {@link #build(CacheStore, boolean)} to construct
     * a validated table, or {@link #decode(byte[])} to parse one from bytes.
     * </p>
     *
     * @param storeVersion cache store version recorded in the table
     * @param tableCrc32   CRC32 of the encoded payload bytes
     * @param archives     archive rows (null becomes empty)
     */
    private ChecksumTable(int storeVersion, int tableCrc32, ArchiveRow[] archives) {
        this.storeVersion = storeVersion;
        this.tableCrc32 = tableCrc32;
        this.archives = archives == null ? new ArchiveRow[0] : archives;
    }

    /**
     * Returns the {@link CacheStore} version that this table corresponds to.
     *
     * <p>
     * This is whatever {@link CacheStore#getVersion()} returned at build time, or what was decoded from bytes.
     * </p>
     *
     * @return store version
     */
    public int getStoreVersion() {
        return storeVersion;
    }

    /**
     * Returns the CRC32 of the encoded payload portion of this table.
     *
     * <p>
     * This is the value written during {@link #encode(ChecksumTable)} and verified during {@link #decode(byte[])}.
     * </p>
     *
     * @return payload CRC32
     */
    public int getTableCrc32() {
        return tableCrc32;
    }

    /**
     * Returns the archive rows contained in this table.
     *
     * <p>
     * The returned array is the internal backing array. Treat it as read-only.
     * </p>
     *
     * @return archive rows (never null)
     */
    public ArchiveRow[] getArchiveRows() {
        return archives;
    }

    /**
     * Builds a checksum table snapshot from a {@link CacheStore}.
     *
     * <p>
     * Each archive row is computed from the bytes produced by {@link CacheArchive#compress(CacheArchive)}.
     * That means the CRC and length represent the exact bytes that would be written/served for that archive.
     * If the archive compression method changes, the bytes change, and the row is considered changed.
     * </p>
     *
     * <p>
     * If {@code includeFileRows} is true, per-file CRC and length rows are generated from each file's raw data.
     * These do <b>not</b> affect archive CRC/length (which remains based on compressed archive bytes), but are
     * included in the encoded payload for deeper diffing/debugging.
     * </p>
     *
     * @param store           cache store to snapshot
     * @param includeFileRows whether to include per-file rows
     * @return new checksum table
     * @throws NullPointerException if store is null
     */
    public static ChecksumTable build(CacheStore store, boolean includeFileRows) {
        Objects.requireNonNull(store, "store");

        CacheArchive[] srcArchives = store.getArchives();
        if (srcArchives == null) srcArchives = new CacheArchive[0];

        ArchiveRow[] rows = new ArchiveRow[srcArchives.length];

        for (int i = 0; i < srcArchives.length; i++) {
            CacheArchive a = srcArchives[i];

            byte[] compressedArchiveBytes = CacheArchive.compress(a);
            int archiveCrc = crc32(compressedArchiveBytes);
            int archiveLen = compressedArchiveBytes.length;

            FileRow[] fileRows = includeFileRows ? buildFileRows(a) : FileRow.EMPTY;

            rows[i] = new ArchiveRow(a.getName(), a.getCompression(), archiveCrc, archiveLen, fileRows);
        }

        byte[] payload = encodePayload(rows);
        int tableCrc = crc32(payload);

        return new ChecksumTable(store.getVersion(), tableCrc, rows);
    }

    /**
     * Builds per-file checksum rows for a single archive.
     *
     * <p>
     * Each {@link FileRow} is computed from the file's raw byte[] data and records a CRC32 plus its length.
     * Null file arrays are treated as empty. Null file data is treated as a zero-length byte array.
     * </p>
     *
     * @param archive archive to inspect
     * @return file rows for the archive (never null)
     */
    private static FileRow[] buildFileRows(CacheArchive archive) {
        CacheFile[] files = archive.getFiles();
        if (files == null) files = new CacheFile[0];

        if (files.length == 0) return FileRow.EMPTY;

        FileRow[] rows = new FileRow[files.length];
        for (int i = 0; i < files.length; i++) {
            CacheFile f = files[i];
            byte[] data = f.getData();
            if (data == null) data = new byte[0];

            rows[i] = new FileRow(f.getName(), crc32(data), data.length);
        }
        return rows;
    }

    /**
     * Encodes a checksum table into bytes suitable for writing to disk or sending over the network.
     *
     * <p>
     * The encoded stream starts with the store version (u16) and the payload CRC32 (i32),
     * followed by the payload bytes. The payload CRC32 is computed during encoding, so it is safe
     * to encode tables built in-memory or tables that were previously decoded.
     * </p>
     *
     * @param table checksum table to encode
     * @return encoded bytes
     * @throws NullPointerException if table is null
     */
    public static byte[] encode(ChecksumTable table) {
        Objects.requireNonNull(table, "table");

        byte[] payload = encodePayload(table.archives);
        int tableCrc = crc32(payload);

        DynamicByteBuffer out = new DynamicByteBuffer();
        out.writeShort((short) table.storeVersion);
        out.writeInt(tableCrc);
        out.writeBytes(payload);
        return out.toTrimmedWriteArray();
    }

    /**
     * Decodes a checksum table from bytes and verifies payload integrity.
     *
     * <p>
     * This method validates the payload CRC32 stored in the header. If the CRC mismatches,
     * an {@link IllegalStateException} is thrown and the table is not returned.
     * </p>
     *
     * @param bytes encoded checksum table bytes
     * @return decoded checksum table
     * @throws NullPointerException  if bytes is null
     * @throws IllegalStateException if payload CRC does not match the expected CRC
     */
    public static ChecksumTable decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");

        ByteBuffer in = ByteBuffer.wrap(bytes);

        int storeVersion = in.getShort();
        int expectedPayloadCrc = in.getInt();

        byte[] payload = new byte[in.remaining()];
        in.get(payload);

        int actualPayloadCrc = crc32(payload);
        if (actualPayloadCrc != expectedPayloadCrc) {
            throw new IllegalStateException("Checksum table CRC mismatch. expected=" + expectedPayloadCrc + " actual=" + actualPayloadCrc);
        }

        ArchiveRow[] rows = decodePayload(payload);
        return new ChecksumTable(storeVersion, expectedPayloadCrc, rows);
    }

    /**
     * Encodes only the payload portion of the table (archives + optional files).
     *
     * <p>
     * This is the section protected by the header CRC32. The payload begins with archiveCount (u16),
     * then emits all archive rows and their file rows.
     * </p>
     *
     * @param archives archive rows (null becomes empty)
     * @return payload bytes
     */
    private static byte[] encodePayload(ArchiveRow[] archives) {
        if (archives == null) archives = new ArchiveRow[0];

        DynamicByteBuffer p = new DynamicByteBuffer();
        p.writeShort((short) archives.length);

        for (ArchiveRow a : archives) {
            writeUtf8(p, a.name());
            p.writeByte(a.compression());
            p.writeInt(a.archiveCrc32());
            p.writeInt(a.archiveLength());

            p.writeShort((short) a.files().length);
            for (FileRow f : a.files()) {
                writeUtf8(p, f.name());
                p.writeInt(f.fileCrc32());
                p.writeInt(f.fileLength());
            }
        }

        return p.toTrimmedWriteArray();
    }

    /**
     * Decodes the payload portion of a checksum table.
     *
     * <p>
     * This method assumes the payload CRC has already been verified by {@link #decode(byte[])}.
     * It parses the archive/file structures and returns the corresponding {@link ArchiveRow} array.
     * </p>
     *
     * @param payload verified payload bytes
     * @return decoded archive rows
     */
    private static ArchiveRow[] decodePayload(byte[] payload) {
        DynamicByteBuffer p = new DynamicByteBuffer(payload);

        int archiveCount = p.readShort();
        ArchiveRow[] rows = new ArchiveRow[archiveCount];

        for (int i = 0; i < archiveCount; i++) {
            String archiveName = readUtf8(p);
            byte compression = p.readByte();
            int archiveCrc = p.readInt();
            int archiveLen = p.readInt();

            int fileCount = p.readShort();
            FileRow[] files = fileCount == 0 ? FileRow.EMPTY : new FileRow[fileCount];

            for (int f = 0; f < fileCount; f++) {
                String fileName = readUtf8(p);
                int fileCrc = p.readInt();
                int fileLen = p.readInt();
                files[f] = new FileRow(fileName, fileCrc, fileLen);
            }

            rows[i] = new ArchiveRow(archiveName, compression, archiveCrc, archiveLen, files);
        }

        return rows;
    }

    /**
     * Finds an archive row by name using case-insensitive comparison.
     *
     * <p>
     * If the provided name is null, this returns null. If no archive matches, this returns null.
     * </p>
     *
     * @param name archive name to search for
     * @return matching archive row, or null if not found
     */
    public ArchiveRow findArchive(String name) {
        if (name == null) return null;
        for (ArchiveRow r : archives) {
            if (r.name().equalsIgnoreCase(name)) return r;
        }
        return null;
    }

    /**
     * Produces a list of archive names that differ between this table and an older table.
     *
     * <p>
     * This is designed for cache invalidation and patching workflows:
     * if an archive's compression changes, length changes, or CRC changes, it is reported as changed.
     * If the old table is null or empty, all current archives are returned.
     * </p>
     *
     * <p>
     * This method only compares archive-level fields (name, compression, archiveLength, archiveCrc32).
     * It does not require file rows and does not compare file rows.
     * </p>
     *
     * @param oldTable previous checksum table (may be null)
     * @return array of changed archive names (never null)
     */
    public String[] diffChangedArchives(ChecksumTable oldTable) {
        if (oldTable == null || oldTable.archives.length == 0) {
            String[] all = new String[archives.length];
            for (int i = 0; i < archives.length; i++) all[i] = archives[i].name();
            return all;
        }

        String[] tmp = new String[archives.length];
        int count = 0;

        for (ArchiveRow now : archives) {
            ArchiveRow old = oldTable.findArchive(now.name());

            if (old == null) {
                tmp[count++] = now.name();
                continue;
            }

            if (old.compression() != now.compression()) {
                tmp[count++] = now.name();
                continue;
            }

            if (old.archiveLength() != now.archiveLength() || old.archiveCrc32() != now.archiveCrc32()) {
                tmp[count++] = now.name();
            }
        }

        String[] out = new String[count];
        System.arraycopy(tmp, 0, out, 0, count);
        return out;
    }

    /**
     * Writes a UTF-8 string with an unsigned byte length prefix.
     *
     * <p>
     * The maximum encoded length is {@link #MAX_NAME_BYTES}. Null strings are encoded as empty.
     * </p>
     *
     * @param b output buffer writer
     * @param s string to write (null becomes "")
     * @throws IllegalArgumentException if the UTF-8 encoding exceeds {@link #MAX_NAME_BYTES}
     */
    private static void writeUtf8(DynamicByteBuffer b, String s) {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);

        if (bytes.length > MAX_NAME_BYTES) {
            throw new IllegalArgumentException("String too long (max " + MAX_NAME_BYTES + " bytes): " + bytes.length);
        }

        b.writeByte((byte) bytes.length);
        b.writeBytes(bytes);
    }

    /**
     * Reads a UTF-8 string that was written with {@link #writeUtf8(DynamicByteBuffer, String)}.
     *
     * <p>
     * The stored length prefix is treated as unsigned (0..255).
     * </p>
     *
     * @param b buffer reader
     * @return decoded UTF-8 string (may be empty)
     */
    private static String readUtf8(DynamicByteBuffer b) {
        int len = b.readByte() & 0xFF;
        return b.readString((byte) len);
    }

    /**
     * Computes a CRC32 value for the provided byte array.
     *
     * <p>
     * This uses {@link CRC32} and returns the lower 32 bits as a signed int.
     * </p>
     *
     * @param bytes input bytes
     * @return CRC32 as int
     */
    private static int crc32(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return (int) crc.getValue();
    }
}