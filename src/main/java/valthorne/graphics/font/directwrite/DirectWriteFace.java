package valthorne.graphics.font.directwrite;

import static valthorne.graphics.font.directwrite.WindowsTextAbi.*;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import static valthorne.graphics.font.directwrite.WindowsCom.*;

/**
 * Backend-owned private DirectWrite font collection loaded from one exact font file.
 * The first face is selected without installing or substituting a similarly named
 * system font. DirectWrite supplies native shaping/fallback and caches at most 256
 * text/size layouts here; callers borrow layouts until eviction or face disposal.
 * Confined to its backend's thread. Closing also deletes its temporary font file.
 */
public final class DirectWriteFace implements AutoCloseable {
    /**
     * Maximum native layouts retained for one face.
     */
    private static final int MAX_LAYOUTS = 256;
    private final DirectWriteBackend backend; // Owner of the native factories/apartment.
    private final Path file; // Exact encoded font snapshot retained for lazy native table reads.
    private final MemorySegment collection; // Owned IDWriteFontCollection1 containing the requested face.
    private final String family; // Native family name read from the private collection.
    private final int weight; // Native weight of the file's first face.
    private final int style; // Native normal/italic/oblique style of the selected face.
    private final int stretch; // Native stretch of the selected face.
    private final LinkedHashMap<DirectWriteLayoutKey, DirectWriteLayout> layouts = new LinkedHashMap<>(32, .75f, true); // Bounded native layout cache.
    private final DirectWriteLayoutKey lookup = new DirectWriteLayoutKey("", 0); // Reused lookup avoids allocating keys for retained text.
    private boolean closed; // Lifetime of the collection, file, and cached layouts.

    /**
     * Creates a private collection from encoded bytes using real DirectWrite factories.
     *
     * @param backend owning native backend
     * @param bytes TrueType/OpenType bytes
     */
    DirectWriteFace(DirectWriteBackend backend, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Font bytes are empty");
        this.backend = backend;
        try {
            file = Files.createTempFile("valthorne-directwrite-", ".font");
            Files.write(file, bytes);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot create private DirectWrite font snapshot", failure);
        }
        WindowsCom com = backend.com();
        MemorySegment reference = MemorySegment.NULL;
        MemorySegment fontFile = MemorySegment.NULL;
        MemorySegment builder = MemorySegment.NULL;
        MemorySegment set = MemorySegment.NULL;
        MemorySegment ownedCollection = MemorySegment.NULL;
        MemorySegment fontFamily = MemorySegment.NULL;
        MemorySegment names = MemorySegment.NULL;
        MemorySegment font = MemorySegment.NULL;
        try (Arena scope = Arena.ofConfined()) {
            MemorySegment output = scope.allocate(ADDRESS);
            com.hr(backend.factory(), WRITE_CREATE_FONT_FILE, new MemoryLayout[]{ADDRESS, ADDRESS, ADDRESS}, wide(scope, file.toAbsolutePath().toString()), MemorySegment.NULL, output);
            fontFile = output.get(ADDRESS, 0);
            MemorySegment supported = scope.allocate(INT);
            MemorySegment fileType = scope.allocate(INT);
            MemorySegment faceType = scope.allocate(INT);
            MemorySegment faceCount = scope.allocate(INT);
            com.hr(fontFile, FONT_FILE_ANALYZE, new MemoryLayout[]{ADDRESS, ADDRESS, ADDRESS, ADDRESS}, supported, fileType, faceType, faceCount);
            if (supported.get(INT, 0) == 0 || faceCount.get(INT, 0) < 1) throw new IllegalArgumentException("DirectWrite does not support this font data");
            com.hr(backend.factory(), WRITE_CREATE_ANALYZED_FACE_REFERENCE, new MemoryLayout[]{ADDRESS, INT, INT, ADDRESS}, fontFile, 0, 0, output);
            reference = output.get(ADDRESS, 0);
            com.hr(backend.factory(), WRITE_CREATE_SET_BUILDER, new MemoryLayout[]{ADDRESS}, output);
            builder = output.get(ADDRESS, 0);
            com.hr(builder, SET_BUILDER_ADD_FACE, new MemoryLayout[]{ADDRESS}, reference);
            com.hr(builder, SET_BUILDER_CREATE_SET, new MemoryLayout[]{ADDRESS}, output);
            set = output.get(ADDRESS, 0);
            com.hr(backend.factory(), WRITE_CREATE_COLLECTION, new MemoryLayout[]{ADDRESS, ADDRESS}, set, output);
            ownedCollection = output.get(ADDRESS, 0);
            com.hr(ownedCollection, COLLECTION_GET_FAMILY, new MemoryLayout[]{INT, ADDRESS}, 0, output);
            fontFamily = output.get(ADDRESS, 0);
            com.hr(fontFamily, FAMILY_GET_NAMES, new MemoryLayout[]{ADDRESS}, output);
            names = output.get(ADDRESS, 0);
            MemorySegment length = scope.allocate(INT);
            com.hr(names, NAMES_GET_LENGTH, new MemoryLayout[]{INT, ADDRESS}, 0, length);
            int count = length.get(INT, 0);
            MemorySegment name = scope.allocate((count + 1L) * 2, 2);
            com.hr(names, NAMES_GET_STRING, new MemoryLayout[]{INT, ADDRESS, INT}, 0, name, count + 1);
            char[] characters = new char[count];
            for (int i = 0; i < count; i++) characters[i] = name.get(ValueLayout.JAVA_CHAR, i * 2L);
            family = new String(characters);
            com.hr(fontFamily, FONT_LIST_GET_FONT, new MemoryLayout[]{INT, ADDRESS}, 0, output);
            font = output.get(ADDRESS, 0);
            weight = (int) com.call(font, FONT_GET_WEIGHT, INT, new MemoryLayout[0]);
            stretch = (int) com.call(font, FONT_GET_STRETCH, INT, new MemoryLayout[0]);
            style = (int) com.call(font, FONT_GET_STYLE, INT, new MemoryLayout[0]);
            collection = ownedCollection;
        } catch (RuntimeException | Error failure) {
            com.release(ownedCollection);
            try {
                Files.deleteIfExists(file);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        } finally {
            com.release(font);
            com.release(names);
            com.release(fontFamily);
            com.release(set);
            com.release(builder);
            com.release(reference);
            com.release(fontFile);
        }
    }

    /**
     * Gets a retained, natively shaped layout for text at an explicit DIP em size.
     *
     * @param text nonnull UTF-16 text
     * @param size finite positive DIPs per em
     * @return borrowed cached layout; valid until cache eviction or face disposal
     */
    public DirectWriteLayout layout(String text, float size) {
        backend.requireOpen();
        if (closed) throw new IllegalStateException("DirectWrite face is closed");
        if (text == null || !Float.isFinite(size) || size <= 0 || size > 4096) throw new IllegalArgumentException("Invalid text or DIP em size");
        lookup.set(text, size);
        DirectWriteLayout found = layouts.get(lookup);
        if (found != null) return found;
        DirectWriteLayout created = new DirectWriteLayout(backend, collection, family, weight, style, stretch, text, size);
        layouts.put(new DirectWriteLayoutKey(text, size), created);
        if (layouts.size() > MAX_LAYOUTS) layouts.pollFirstEntry().getValue().close();
        return created;
    }

    /**
     * Returns the family reported by DirectWrite for the exact private font.
     *
     * @return native font family name
     */
    public String family() { return family; }

    @Override
    public void close() {
        if (closed) return;
        for (DirectWriteLayout layout : layouts.values()) layout.close();
        layouts.clear();
        backend.com().release(collection);
        closed = true;
        backend.forget(this);
        try {
            Files.deleteIfExists(file);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot delete private DirectWrite font snapshot", failure);
        }
    }
}
