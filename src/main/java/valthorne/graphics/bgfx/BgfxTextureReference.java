package valthorne.graphics.bgfx;

/**
 * Resolves a stable engine texture identifier to a current device allocation.
 */
interface BgfxTextureReference {
    /**
     * Returns the live native allocation without transferring ownership.
     *
     * @return native sampled texture handle
     */
    short handle();
}
