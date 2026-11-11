package valthorne.graphics.bgfx;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Keeps public sampled texture IDs stable across device replacement. IDs above
 * the native unsigned-short range resolve through a dense resource table. Slots
 * recycle on disposal; callers must not use IDs after their owners are disposed.
 */
final class BgfxTextureHandles {
    /**
     * Separates engine identifiers from native unsigned-short texture handles.
     */
    private static final int FIRST_ID = 65536;
    /**
     * Live resource references indexed by engine slot.
     */
    private static final ArrayList<BgfxTextureReference> textures = new ArrayList<>();
    /**
     * Reusable primitive free-slot stack, grown only during disposal peaks.
     */
    private static int[] free = new int[16];
    /**
     * Number of available recycled slots.
     */
    private static int freeCount;

    /**
     * Prevents constructing the texture identifier table.
     */
    private BgfxTextureHandles() {
    }

    /**
     * Allocates an engine slot for a successfully created resource.
     *
     * @param texture live native texture owner
     * @return stable positive engine identifier
     */
    static int register(BgfxTextureReference texture) {
        /*
         * Recycling bounds storage by peak live textures rather than lifetime churn.
         */
        if (freeCount != 0) {
            int slot = free[--freeCount];
            textures.set(slot, texture);
            return FIRST_ID + slot;
        }
        int slot = textures.size();
        textures.add(texture);
        return FIRST_ID + slot;
    }

    /**
     * Resolves engine identifiers and passes native handles through unchanged.
     *
     * @param id engine ID or native texture index
     * @return current native texture index
     */
    static int resolve(int id) {
        /*
         * Native font/attachment bindings retain their direct fast path.
         */
        if (id < FIRST_ID) return id;
        return Short.toUnsignedInt(reference(id).handle());
    }

    /**
     * Resolves the owner of an engine texture ID at an API lifetime boundary.
     *
     * @param id engine texture identifier
     * @return live texture owner
     */
    static BgfxTextureReference reference(int id) {
        /*
         * Native handles have no engine ownership record and cannot be disposed here.
         */
        int slot = id - FIRST_ID;
        if (slot < 0 || slot >= textures.size() || textures.get(slot) == null) throw new IllegalStateException("Texture is disposed or not engine-owned");
        return textures.get(slot);
    }

    /**
     * Removes an engine ID after native destruction.
     *
     * @param id allocated engine identifier
     */
    static void unregister(int id) {
        /*
         * Native-only standalone allocations have no engine slot to release.
         */
        if (id < FIRST_ID) return;
        int slot = id - FIRST_ID;
        if (textures.set(slot, null) == null) return;
        BgfxGraphics.resetTextureBinding(id);
        if (freeCount == free.length) free = Arrays.copyOf(free, free.length * 2);
        free[freeCount++] = slot;
    }

    /**
     * Releases references when the engine runtime ends.
     */
    static void clear() {
        /*
         * Engine identifiers do not survive a complete application shutdown.
         */
        textures.clear();
        freeCount = 0;
    }
}
