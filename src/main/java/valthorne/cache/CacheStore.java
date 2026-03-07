package valthorne.cache;

import valthorne.cache.checksum.ChecksumTable;
import valthorne.io.buffer.DynamicByteBuffer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Top-level container for a Valthorne cache on disk.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Build a store in memory.
 * CacheStore store = new CacheStore();
 * store.setVersion(1);
 *
 * CacheArchive sprites = new CacheArchive("sprites",
 *         new CacheFile("player.png", playerPngBytes),
 *         new CacheFile("enemy.png", enemyPngBytes)
 * );
 * sprites.setCompression((byte) 1); // Example: GZIP in your archive format (see CacheArchive constants).
 *
 * store.setArchives(sprites);
 *
 * // Save to disk.
 * store.save(Path.of("assets/cache.vthn"));
 *
 * // Load it back.
 * CacheStore loaded = CacheStore.load(Path.of("assets/cache.vthn"));
 * CacheArchive a = loaded.getArchive("sprites");
 * CacheFile f = a.getFile("player.png");
 * byte[] bytes = f.getData();
 *
 * // Modify.
 * loaded.removeArchive("sprites");
 * loaded.addArchive(new CacheArchive("ui"));
 * loaded.save(Path.of("assets/cache.vthn"));
 * }</pre>
 *
 * <h2>File format</h2>
 * <ul>
 *     <li>{@code short}: store version</li>
 *     <li>{@code short}: archive count</li>
 * </ul>
 *
 * <p>
 * Each archive entry then stores:
 * </p>
 * <ul>
 *     <li>{@code byte}: compression id</li>
 *     <li>{@code int}: compressed payload byte length</li>
 *     <li>{@code byte[]}: payload bytes (compressed or raw depending on compression id)</li>
 * </ul>
 *
 * <p>
 * Archive payload encoding/decoding is handled by {@link CacheArchive#compress(CacheArchive)} and
 * {@link CacheArchive#decompress(byte[], byte)}.
 * </p>
 * Alongside the cache file, {@link #save(Path)} will also write a checksum table file:
 * </p>
 * <ul>
 *     <li>{@code cache.vthn} -> main cache</li>
 *     <li>{@code cache.chk} -> checksum table (manifest)</li>
 * </ul>
 *
 * @author Albert Beaupre
 * @since March 3rd, 2026
 */
public class CacheStore {

    private CacheArchive[] archives; // Archives contained by this store (ordered by index/id).
    private ChecksumTable checksums;
    private int version; // Store format version written as an unsigned short.

    /**
     * Sets the store format version that will be written by {@link #save(Path)}.
     *
     * <p>
     * This value is stored as a {@code short} in the file. Keep it within 0..65535 if you treat it as
     * an unsigned short on read.
     * </p>
     *
     * @param version store version
     */
    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * Returns the store format version read from disk or set in memory.
     *
     * @return version value stored in this instance
     */
    public int getVersion() {
        return version;
    }

    /**
     * Removes an archive by numeric id (array index), compacting the internal archive array.
     *
     * <p>
     * If the store has no archives, this returns {@code false}. If the id is invalid, this throws
     * {@link IndexOutOfBoundsException}. On success, the internal array shrinks by 1 and all entries
     * after the removed id shift left by one position.
     * </p>
     *
     * <p>
     * This is a structural removal. Any external code holding on to a previously obtained archive id
     * must be aware that ids can change after removal.
     * </p>
     *
     * @param id archive id (0-based)
     * @return true if an archive was removed, false if the store had no archives
     * @throws IndexOutOfBoundsException if id is not within [0, archives.length)
     */
    public boolean removeArchive(int id) {
        if (archives == null || archives.length == 0) return false;

        if (id < 0 || id >= archives.length) throw new IndexOutOfBoundsException("Invalid archive ID: " + id);

        CacheArchive[] newArchives = new CacheArchive[archives.length - 1];
        System.arraycopy(archives, 0, newArchives, 0, id);
        System.arraycopy(archives, id + 1, newArchives, id, archives.length - id - 1);
        archives = newArchives;

        return true;
    }

    /**
     * Removes the first archive whose name matches (case-insensitive).
     *
     * <p>
     * If the store has no archives, this returns {@code false}. If the name is null, this throws
     * {@link NullPointerException}. If no matching archive exists, this returns {@code false}.
     * </p>
     *
     * <p>
     * Internally this performs a linear search and then delegates to {@link #removeArchive(int)}.
     * </p>
     *
     * @param name archive name to remove
     * @return true if removed, false if not found or store empty
     * @throws NullPointerException if name is null
     */
    public boolean removeArchive(String name) {
        if (name == null) throw new NullPointerException("Cannot remove a null archive name from the cache store");

        if (archives == null || archives.length == 0) return false;

        for (int i = 0; i < archives.length; i++) {
            if (archives[i].getName().equalsIgnoreCase(name)) return removeArchive(i);
        }

        return false;
    }

    /**
     * Replaces an archive at the given numeric id.
     *
     * <p>
     * This does not resize the store. It only replaces the slot. If the store has no archives,
     * this throws {@link IllegalStateException}. If the id is invalid, this throws
     * {@link IndexOutOfBoundsException}. If the archive is null, this throws {@link NullPointerException}.
     * </p>
     *
     * @param id      archive id (0-based)
     * @param archive archive instance to store
     * @throws NullPointerException      if archive is null
     * @throws IllegalStateException     if the store contains no archives
     * @throws IndexOutOfBoundsException if id is not within [0, archives.length)
     */
    public void setArchive(int id, CacheArchive archive) {
        if (archive == null) throw new NullPointerException("Cannot set a null archive in the cache store");

        if (archives == null || archives.length == 0)
            throw new IllegalStateException("Cache store contains no archives");

        if (id < 0 || id >= archives.length) throw new IndexOutOfBoundsException("Invalid archive ID: " + id);

        archives[id] = archive;
    }

    /**
     * Replaces the first archive whose name matches (case-insensitive).
     *
     * <p>
     * This is intended for "swap in" behavior when you already know the archive exists.
     * If no archive matches the name, this throws {@link IllegalArgumentException}.
     * </p>
     *
     * <p>
     * If the provided name is null or the provided archive is null, this throws {@link NullPointerException}.
     * </p>
     *
     * @param name    archive name to find
     * @param archive new archive instance to store
     * @throws NullPointerException     if name or archive is null
     * @throws IllegalArgumentException if no archive with that name exists
     */
    public void setArchive(String name, CacheArchive archive) {
        if (name == null) throw new NullPointerException("Cannot search for a null archive name in the cache store");

        if (archive == null) throw new NullPointerException("Cannot set a null archive in the cache store");

        for (int i = 0; i < archives.length; i++) {
            if (archives[i].getName().equalsIgnoreCase(name)) {
                archives[i] = archive;
                return;
            }
        }

        throw new IllegalArgumentException("Archive not found: " + name);
    }

    /**
     * Returns the internal archive array reference.
     *
     * <p>
     * This is the live backing array. Modifying its contents will modify this store. If you need
     * immutability, copy the array before exposing it.
     * </p>
     *
     * @return internal archive array (may be null)
     */
    public CacheArchive[] getArchives() {
        return archives;
    }

    /**
     * Returns the first archive whose name matches (case-insensitive).
     *
     * <p>
     * This performs a linear search. If no archive matches, returns {@code null}. If name is null,
     * throws {@link NullPointerException}.
     * </p>
     *
     * @param name archive name to search
     * @return archive if found, otherwise null
     * @throws NullPointerException if name is null
     */
    public CacheArchive getArchive(String name) {
        if (name == null) throw new NullPointerException("Cannot search for a null archive name in the cache store");
        for (CacheArchive archive : archives) {
            if (archive.getName().equalsIgnoreCase(name)) return archive;
        }
        return null;
    }

    /**
     * Returns an archive by numeric id (array index).
     *
     * <p>
     * This is a direct indexed access. If the id is invalid, throws {@link IndexOutOfBoundsException}.
     * </p>
     *
     * @param id archive id (0-based)
     * @return archive at that index
     * @throws IndexOutOfBoundsException if id is not within [0, archives.length)
     */
    public CacheArchive getArchive(int id) {
        if (id < 0 || id >= archives.length) throw new IndexOutOfBoundsException("Invalid archive ID: " + id);
        return archives[id];
    }

    /**
     * Appends an archive to the end of the store, resizing the internal array.
     *
     * <p>
     * If the store currently has no archives, this allocates an array of size 1. Otherwise, it grows
     * by 1 and copies all existing references.
     * </p>
     *
     * <p>
     * The store enforces a maximum count of {@code Short.MAX_VALUE} archives because the archive count
     * is stored as a {@code short} in the on-disk format.
     * </p>
     *
     * @param archive archive to add
     * @throws NullPointerException     if archive is null
     * @throws IllegalArgumentException if archive count would exceed {@code Short.MAX_VALUE}
     */
    public void addArchive(CacheArchive archive) {
        if (archive == null) throw new NullPointerException("Cannot add a null archive to the cache store");
        if (archives == null) archives = new CacheArchive[1];
        else {
            if (archives.length >= Short.MAX_VALUE)
                throw new IllegalArgumentException("Cache store cannot contain more than " + Short.MAX_VALUE + " archives");
            CacheArchive[] newArchives = new CacheArchive[archives.length + 1];
            System.arraycopy(archives, 0, newArchives, 0, archives.length);
            archives = newArchives;
        }
        archives[archives.length - 1] = archive;
    }

    /**
     * Replaces the archive array used by this store.
     *
     * <p>
     * This is a direct assignment of the provided varargs array reference (no copy).
     * The store enforces {@code Short.MAX_VALUE} because the count is stored as a short on disk.
     * </p>
     *
     * @param archives new archives backing array (varargs)
     * @throws IllegalArgumentException if archive count is >= {@code Short.MAX_VALUE}
     */
    public void setArchives(CacheArchive... archives) {
        if (archives.length >= Short.MAX_VALUE)
            throw new IllegalArgumentException("Cache store cannot contain more than " + Short.MAX_VALUE + " archives");
        this.archives = archives;
    }

    /**
     * Saves the current state of the cache store to the specified file path,
     * writing its archives and associated data. A corresponding checksum file
     * with the ".chk" extension is also created alongside the main file.
     *
     * @param path the file path where the cache store and its checksum data
     *             will be written
     * @throws RuntimeException if an {@code IOException} occurs during
     *                          any file write operations
     */
    public void save(Path path) {
        DynamicByteBuffer buffer = new DynamicByteBuffer();
        buffer.writeShort((short) version);
        buffer.writeShort((short) archives.length);

        for (CacheArchive archive : archives) {
            byte[] compressed = CacheArchive.compress(archive);
            buffer.writeByte(archive.getCompression());
            buffer.writeInt(compressed.length);
            buffer.writeBytes(compressed);
        }

        try {
            Files.write(path, buffer.toTrimmedWriteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            this.checksums = ChecksumTable.build(this, true);

            String fileName = path.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String base = dot == -1 ? fileName : fileName.substring(0, dot);

            Path checksumPath = path.resolveSibling(base + ".chk");

            byte[] checksumBytes = ChecksumTable.encode(checksums);
            Files.write(checksumPath, checksumBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a cache store from a file at the specified path.
     * <p>
     * This method reads the file data and attempts to decode it into a {@code CacheStore} instance.
     * If a matching checksum file exists in the same location with a ".chk" extension, it will also parse
     * and associate the checksum data with the loaded store.
     *
     * @param path the path to the cache store file to be loaded
     * @return a {@code CacheStore} instance initialized from the file data
     * @throws RuntimeException if an {@code IOException} occurs during file read operations
     */
    public static CacheStore load(Path path) {
        try {
            CacheStore store = load(Files.readAllBytes(path));

            String fileName = path.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String base = dot == -1 ? fileName : fileName.substring(0, dot);

            Path checksumPath = path.resolveSibling(base + ".chk");

            if (Files.exists(checksumPath)) {
                byte[] checksumBytes = Files.readAllBytes(checksumPath);
                store.checksums = ChecksumTable.decode(checksumBytes);
            }

            return store;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a cache store from its raw bytes.
     *
     * <p>
     * This validates the version and archive count, then reads each archive payload
     * and delegates archive decoding to {@link CacheArchive#decompress(byte[], byte)}.
     * </p>
     *
     * @param data raw file bytes
     * @return loaded cache store
     */
    public static CacheStore load(byte[] data) {
        DynamicByteBuffer buffer = new DynamicByteBuffer(data);
        int version = buffer.readShort();
        int archiveCount = buffer.readShort();

        CacheArchive[] archives = new CacheArchive[archiveCount];
        CacheStore store = new CacheStore();
        store.version = version;
        store.archives = archives;

        for (int archiveID = 0; archiveID < archiveCount; archiveID++) {
            byte compression = buffer.readByte();
            int compressedSize = buffer.readInt();
            byte[] payload = buffer.readBytes(compressedSize);
            archives[archiveID] = CacheArchive.decompress(payload, compression);
        }
        return store;
    }
}