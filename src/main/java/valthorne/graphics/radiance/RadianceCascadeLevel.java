package valthorne.graphics.radiance;

import valthorne.graphics.texture.Texture;

/**
 * Stores one level's probe geometry and three owned floating-point textures: an
 * interval target, a scratch target used for alternating extension passes, and a
 * merged output target. Rays are packed horizontally within each probe row.
 *
 * <p>The enclosing solver creates and disposes levels on its graphics thread.
 * Public texture access is borrowed and becomes invalid when the solver rebuilds or
 * disposes its targets. Geometry is fixed for the lifetime of this level; swapping
 * interval targets changes only which stored image is considered current.</p>
 *
 * @author Albert Beaupre
 */
public final class RadianceCascadeLevel {

    private final int index; // Fixed zero-based level index.
    private final int probeSpacing; // Fixed probe spacing in solver pixels.
    private final int rayCount; // Fixed directional rays per probe.
    private final int raySide; // Fixed compatibility ray-side value, equal to ray count.
    private final int traceCount; // Fixed trace count, equal to ray count.
    private final int probeCountX; // Fixed horizontal probe count.
    private final int probeCountY; // Fixed vertical probe count.
    private final int textureWidth; // Fixed packed texture width in pixels.
    private final int textureHeight; // Fixed packed texture height in pixels.
    private final int extensionPassCount; // Fixed number of interval extension passes, equal to level index.
    private final float intervalStart; // Fixed interval start distance in solver units.
    private final float intervalLength; // Fixed full interval length in solver units.
    private final RadianceRenderTarget intervalTarget; // Owned initial interval texture.
    private final RadianceRenderTarget scratchTarget; // Owned alternate extension destination.
    private final RadianceRenderTarget mergedTarget; // Owned merged radiance output.

    private boolean intervalUsesScratch; // Selects the scratch texture as the current interval image.

    /**
     * Retains level geometry and allocates three nearest-filtered RGBA16F textures.
     * Width is max(1, probeCountX * rayCount) and height is max(1, probeCountY).
     * The solver must supply consistent positive geometry; this constructor does not
     * validate index, interval values, or multiplication overflow.
     *
     * @param index zero-based level index and extension-pass count
     * @param probeSpacing spacing in solver pixels
     * @param rayCount directional rays per probe
     * @param probeCountX horizontal probes
     * @param probeCountY vertical probes
     * @param intervalStart interval start in solver units
     * @param intervalLength interval length in solver units
     */
    RadianceCascadeLevel(int index, int probeSpacing, int rayCount, int probeCountX, int probeCountY, float intervalStart, float intervalLength) {
        this.index = index;
        this.probeSpacing = probeSpacing;
        this.rayCount = rayCount;
        this.raySide = rayCount;
        this.traceCount = rayCount;
        this.probeCountX = probeCountX;
        this.probeCountY = probeCountY;
        this.textureWidth = Math.max(1, probeCountX * rayCount);
        this.textureHeight = Math.max(1, probeCountY);
        this.extensionPassCount = index;
        this.intervalStart = intervalStart;
        this.intervalLength = intervalLength;
        this.intervalTarget = new RadianceRenderTarget(textureWidth, textureHeight, false, false);
        this.scratchTarget = new RadianceRenderTarget(textureWidth, textureHeight, false, false);
        this.mergedTarget = new RadianceRenderTarget(textureWidth, textureHeight, false, false);
    }

    /**
     * Returns this level's zero-based level index.
     * The value is fixed at construction and remains available after texture disposal.
     *
     * @return zero-based level index
     */
    public int getIndex() {
        return index;
    }

    /**
     * Returns this level's probe spacing in solver pixels.
     * The value is fixed at construction and remains available after texture disposal.
     *
     * @return probe spacing in solver pixels
     */
    public int getProbeSpacing() {
        return probeSpacing;
    }

    /**
     * Returns this level's directional rays per probe.
     * The value is fixed at construction and remains available after texture disposal.
     *
     * @return directional rays per probe
     */
    public int getRayCount() {
        return rayCount;
    }

    /**
     * Returns this level's compatibility ray-side value, equal to ray count.
     * The value is fixed at construction and remains available after texture disposal.
     *
     * @return compatibility ray-side value, equal to ray count
     */
    public int getRaySide() {
        return raySide;
    }

    /**
     * Returns this level's trace count, equal to ray count.
     * The value is fixed at construction and remains available after texture disposal.
     *
     * @return trace count, equal to ray count
     */
    public int getTraceCount() {
        return traceCount;
    }

    /**
     * Returns this level's horizontal probe count.
     * The value is fixed at construction and remains available after texture disposal.
     *
     * @return horizontal probe count
     */
    public int getProbeCountX() {
        return probeCountX;
    }

    /**
     * Returns this level's vertical probe count.
     * The value is fixed at construction and remains available after texture disposal.
     *
     * @return vertical probe count
     */
    public int getProbeCountY() {
        return probeCountY;
    }

    /**
     * Returns this level's packed texture width in pixels.
     * The value is fixed at construction and remains available after texture disposal.
     *
     * @return packed texture width in pixels
     */
    public int getTextureWidth() {
        return textureWidth;
    }

    /**
     * Returns this level's packed texture height in pixels.
     * The value is fixed at construction and remains available after texture disposal.
     *
     * @return packed texture height in pixels
     */
    public int getTextureHeight() {
        return textureHeight;
    }

    /**
     * Returns this level's number of interval extension passes, equal to level index.
     * The value is fixed at construction and remains available after texture disposal.
     *
     * @return number of interval extension passes, equal to level index
     */
    public int getExtensionPassCount() {
        return extensionPassCount;
    }

    /**
     * Returns this level's interval start distance in solver units.
     * The value is fixed at construction and remains available after texture disposal.
     *
     * @return interval start distance in solver units
     */
    public float getIntervalStart() {
        return intervalStart;
    }

    /**
     * Returns this level's full interval length in solver units.
     * The value is fixed at construction and remains available after texture disposal.
     *
     * @return full interval length in solver units
     */
    public float getIntervalLength() {
        return intervalLength;
    }

    /**
     * Returns the merged-output texture name without binding it or transferring ownership.
     *
     * @return merged texture name, or zero after disposal
     */
    public int getTextureID() {
        return mergedTarget.getTextureID();
    }

    /**
     * Returns a borrowed wrapper for the merged output. Do not dispose it separately
     * or use it after the enclosing solver rebuilds or disposes this level.
     *
     * @return merged output wrapper, or null after disposal
     */
    public Texture getTexture() {
        return mergedTarget.getTexture();
    }

    /**
     * Selects the initial interval target as current before a new trace. Does not clear
     * or copy either image; tracing is responsible for writing the selected destination.
     */
    void resetIntervalBuild() {
        intervalUsesScratch = false;
    }

    /**
     * Returns the current interval image selected by the alternating-target flag.
     *
     * @return current interval texture name
     */
    int getCurrentIntervalTextureID() {
        return (intervalUsesScratch ? scratchTarget : intervalTarget).getTextureID();
    }

    /**
     * Returns the other interval image for the next extension destination. Reading the
     * name does not swap targets or establish GPU synchronization.
     *
     * @return alternate interval texture name
     */
    int getAlternateIntervalTextureID() {
        return (intervalUsesScratch ? intervalTarget : scratchTarget).getTextureID();
    }

    /**
     * Exchanges the logical current and alternate interval images after an extension
     * pass. No texture data is copied and no memory barrier is issued here.
     */
    void swapIntervalTargets() {
        intervalUsesScratch = !intervalUsesScratch;
    }

    /**
     * Returns the owned merge destination for internal compute dispatch.
     *
     * @return merged texture name, or zero after disposal
     */
    int getMergedTextureID() {
        return mergedTarget.getTextureID();
    }

    /**
     * Releases all three owned texture targets. Geometry metadata remains available,
     * but prior wrappers must no longer be used. Requires the owning graphics context.
     */
    void dispose() {
        intervalTarget.dispose();
        scratchTarget.dispose();
        mergedTarget.dispose();
    }
}
