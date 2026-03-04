package valthorne.cache;

import valthorne.compression.CompressionStrategy;
import valthorne.io.buffer.DynamicByteBuffer;

import java.nio.charset.StandardCharsets;

/**
 * A single named archive inside a {@link CacheStore}.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * CacheArchive archive = new CacheArchive("ui");
 * archive.setCompression((byte) 1); // Example: gzip in your format.
 *
 * archive.addFile(new CacheFile("button.png", buttonBytes));
 * archive.addFile(new CacheFile("panel.png", panelBytes));
 *
 * // Store it.
 * CacheStore store = new CacheStore();
 * store.setVersion(1);
 * store.setArchives(archive);
 *
 * // Encode for disk.
 * byte[] payload = CacheArchive.compress(archive);
 *
 * // Decode from disk.
 * CacheArchive decoded = CacheArchive.decompress(payload, archive.getCompression());
 * CacheFile f = decoded.getFile("button.png");
 * }</pre>
 *
 * <h2>Archive payload format</h2>
 * <p>
 * The (decompressed) archive payload begins with:
 * </p>
 * <ul>
 *     <li>{@code short}: file count</li>
 *     <li>{@code byte}: archive name byte length (UTF-8)</li>
 *     <li>{@code byte[]}: archive name bytes</li>
 * </ul>
 *
 * <p>
 * Each file then stores:
 * </p>
 * <ul>
 *     <li>{@code int}: file data size</li>
 *     <li>{@code byte}: file name byte length (UTF-8)</li>
 *     <li>{@code byte[]}: file name bytes</li>
 *     <li>{@code byte[]}: file data bytes</li>
 * </ul>
 *
 * <p>
 * The outer store decides whether the payload is compressed. Compression choice is stored in
 * {@link #compression} and written by {@link CacheStore#save(java.nio.file.Path)}.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 3rd, 2026
 */
public class CacheArchive {

    private static final byte NO_COMPRESSION = 0; // Compression id meaning "store raw bytes".
    private static final byte GZIP = 1; // Compression id meaning "gzip compressed payload".
    private static final byte BZIP2 = 2; // Compression id meaning "bzip2 compressed payload"
    private static final byte LZMA = 3; // Compression id meaning "lzma compressed payload"
    private static final byte XZ = 4; // Compression id meaning "xz compressed payload"

    private String name; // Archive name (stored as UTF-8 in payload).
    private byte compression; // Compression id used when storing this archive in a CacheStore.
    private CacheFile[] files; // Files contained in this archive (ordered by index/id).

    /**
     * Creates a new archive with a name and an initial file array.
     *
     * <p>
     * The provided {@code files} varargs becomes the backing array reference (no defensive copy).
     * If you want to prevent external mutation, pass a copied array.
     * </p>
     *
     * @param name  archive name
     * @param files initial files (may be empty)
     */
    public CacheArchive(String name, CacheFile... files) {
        this.name = name;
        this.files = files;
    }

    /**
     * Appends a file to this archive, resizing the internal file array.
     *
     * <p>
     * This performs an array grow-by-one and copies all existing references. The archive enforces
     * {@code Short.MAX_VALUE} because file counts are stored as a {@code short} in the payload.
     * </p>
     *
     * @param file file to add
     * @throws IllegalArgumentException if file count would exceed {@code Short.MAX_VALUE}
     */
    public void addFile(CacheFile file) {
        if (files.length >= Short.MAX_VALUE)
            throw new IllegalArgumentException("Archive cannot contain more than " + Short.MAX_VALUE + " files");
        CacheFile[] newFiles = new CacheFile[files.length + 1];
        System.arraycopy(files, 0, newFiles, 0, files.length);
        newFiles[files.length] = file;
        files = newFiles;
    }

    /**
     * Decodes an archive from payload bytes and compression id.
     *
     * <p>
     * If {@code compression == NO_COMPRESSION}, {@code payload} is treated as already decompressed.
     * Otherwise, the appropriate {@link CompressionStrategy} is used to decompress the bytes first.
     * </p>
     *
     * <p>
     * This then parses the archive payload format and constructs {@link CacheFile} objects for each file.
     * The returned archive has its {@link #compression} field set to the provided id.
     * </p>
     *
     * @param payload     raw stored bytes (compressed or uncompressed)
     * @param compression compression id
     * @return decoded archive
     */
    public static CacheArchive decompress(byte[] payload, byte compression) {
        CompressionStrategy strategy = getStrategy(compression);
        byte[] decompressed = strategy == null ? payload : strategy.decompress(payload);

        DynamicByteBuffer archiveBuffer = new DynamicByteBuffer(decompressed);
        int fileCount = archiveBuffer.readShort();
        byte nameSize = archiveBuffer.readByte();
        String name = archiveBuffer.readString(nameSize);

        CacheFile[] files = new CacheFile[fileCount];
        CacheArchive archive = new CacheArchive(name, files);
        archive.compression = compression;

        for (int fileID = 0; fileID < fileCount; fileID++) {
            int fileSize = archiveBuffer.readInt();
            nameSize = archiveBuffer.readByte();
            name = archiveBuffer.readString(nameSize);
            byte[] fileData = archiveBuffer.readBytes(fileSize);

            files[fileID] = new CacheFile(name, fileData);
        }
        archive.setFiles(files);
        return archive;
    }

    /**
     * Encodes an archive into payload bytes, applying its configured compression if requested.
     *
     * <p>
     * This always writes the decompressed payload format first (file count, archive name, files...).
     * If {@link #compression} is {@code NO_COMPRESSION}, the raw payload bytes are returned directly.
     * Otherwise, the payload is compressed using the strategy returned by {@link #getStrategy(int)}.
     * </p>
     *
     * @param archive archive to encode
     * @return payload bytes (compressed or raw)
     * @throws RuntimeException if the configured compression id is unsupported
     */
    public static byte[] compress(CacheArchive archive) {
        DynamicByteBuffer buffer = new DynamicByteBuffer();
        buffer.writeShort((short) archive.files.length);

        byte[] nameBytes = archive.name.getBytes(StandardCharsets.UTF_8);
        buffer.writeByte((byte) nameBytes.length);
        buffer.writeBytes(nameBytes);
        for (CacheFile file : archive.files) {
            byte[] fileData = file.getData();
            buffer.writeInt(fileData.length);
            byte[] fileNameBytes = file.getName().getBytes(StandardCharsets.UTF_8);
            buffer.writeByte((byte) fileNameBytes.length);
            buffer.writeBytes(fileNameBytes);
            buffer.writeBytes(fileData);
        }

        byte[] data = buffer.toTrimmedWriteArray();
        if (archive.compression == NO_COMPRESSION) return data;

        CompressionStrategy strategy = getStrategy(archive.compression);
        if (strategy == null) {
            throw new RuntimeException("Unsupported compression algorithm: " + archive.compression);
        } else {
            return strategy.compress(data);
        }
    }

    /**
     * Returns the first file whose name matches (case-insensitive).
     *
     * <p>
     * This performs a linear search. If no file matches, returns {@code null}.
     * </p>
     *
     * @param name file name to search
     * @return file if found, otherwise null
     */
    public CacheFile getFile(String name) {
        for (CacheFile file : files) {
            if (file.getName().equalsIgnoreCase(name)) return file;
        }
        return null;
    }

    /**
     * Returns a file by numeric id (array index).
     *
     * <p>
     * This is a direct indexed access. If the id is invalid, the JVM will throw
     * {@link ArrayIndexOutOfBoundsException}.
     * </p>
     *
     * @param id file id (0-based)
     * @return file at that index
     */
    public CacheFile getFile(int id) {
        return files[id];
    }

    /**
     * Returns the internal file array reference.
     *
     * <p>
     * This is the live backing array. Modifying its contents will modify this archive. Copy it if you
     * need immutability.
     * </p>
     *
     * @return file array
     */
    public CacheFile[] getFiles() {
        return files;
    }

    /**
     * Replaces the file array used by this archive.
     *
     * <p>
     * This is a direct assignment of the provided varargs array reference (no copy).
     * The archive enforces {@code Short.MAX_VALUE} because file counts are stored as a short on disk.
     * </p>
     *
     * @param files new file array (varargs)
     * @throws IllegalArgumentException if file count is >= {@code Short.MAX_VALUE}
     */
    public void setFiles(CacheFile... files) {
        if (files.length >= Short.MAX_VALUE)
            throw new IllegalArgumentException("Archive cannot contain more than " + Short.MAX_VALUE + " files");
        this.files = files;
    }

    /**
     * Returns the archive name.
     *
     * @return archive name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the archive name.
     *
     * <p>
     * This validates against {@code Byte.MAX_VALUE} because the name length is written as a single byte
     * in the payload. If UTF-8 encoding could exceed this even when {@code name.length()} does not,
     * you may want to validate based on {@code name.getBytes(UTF_8).length} instead.
     * </p>
     *
     * @param name new archive name
     * @throws IllegalArgumentException if name length is >= {@code Byte.MAX_VALUE}
     */
    public void setName(String name) {
        if (name.length() >= Byte.MAX_VALUE)
            throw new IllegalArgumentException("Archive name cannot be >= " + Byte.MAX_VALUE + " bytes");
        this.name = name;
    }

    /**
     * Returns the compression id used for this archive when stored in a {@link CacheStore}.
     *
     * @return compression id
     */
    public byte getCompression() {
        return compression;
    }

    /**
     * Sets the compression id for this archive.
     *
     * <p>
     * Valid ids are currently 0..4 (NO_COMPRESSION, GZIP, BZIP2, LZMA, XZ).
     * </p>
     *
     * @param compression compression id
     * @throws IllegalArgumentException if id is outside the allowed range
     */
    public void setCompression(byte compression) {
        if (compression < NO_COMPRESSION || compression > XZ)
            throw new IllegalArgumentException("Compression must be either 1=NONE, 2=GZIP, 3=BZIP2, 4=LZMA, or 5=XZ");
        this.compression = compression;
    }

    /**
     * Maps compression id to a {@link CompressionStrategy}.
     *
     * <p>
     * Returning {@code null} indicates NO_COMPRESSION. If you add new compression types, update this
     * method to return their strategy.
     * </p>
     *
     * @param compression compression id
     * @return strategy or null for no compression
     * @throws IllegalStateException if id is unknown
     */
    private static CompressionStrategy getStrategy(int compression) {
        return switch (compression) {
            case NO_COMPRESSION -> null;
            case GZIP -> CompressionStrategy.GZIP;
            case BZIP2 -> CompressionStrategy.BZIP2;
            case LZMA -> CompressionStrategy.LZMA;
            case XZ -> CompressionStrategy.XZ;
            default -> throw new IllegalStateException("Unexpected value: " + compression);
        };
    }
}