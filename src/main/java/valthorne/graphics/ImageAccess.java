package valthorne.graphics;

import static org.lwjgl.bgfx.BGFX.*;

/**
 * Declares how one compute dispatch accesses an image or storage buffer.
 */
public enum ImageAccess {
    /**
     * Read existing resource values.
     */
    READ(BGFX_ACCESS_READ),
    /**
     * Write resource values.
     */
    WRITE(BGFX_ACCESS_WRITE),
    /**
     * Read and write resource values.
     */
    READ_WRITE(BGFX_ACCESS_READWRITE);

    private final int nativeAccess; // Portable native access mode.

    /**
     * Stores the native access contract.
     *
     * @param nativeAccess renderer-independent access mode
     */
    ImageAccess(int nativeAccess) {
        this.nativeAccess = nativeAccess;
    }

    /**
     * Returns the native binding access mode.
     *
     * @return portable access identifier
     */
    public int nativeAccess() {
        return nativeAccess;
    }
}
