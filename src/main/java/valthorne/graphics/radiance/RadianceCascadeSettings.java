package valthorne.graphics.radiance;

/**
 * Mutable configuration retained by a radiance-cascade solver. Configure hierarchy
 * dimensions before constructing the solver: changing them later does not itself
 * rebuild textures. Shading controls read during render can affect the next solve.
 * The Flatland implementation requires a branch factor of two.
 *
 * <p>Defaults use probe spacing two, four rays, an automatically derived base interval,
 * half-resolution solving, no explicit level limit, and an 8192 texture-dimension cap.
 * Float range checks do not separately reject NaN; supply finite values for meaningful
 * rendering. Ray-step and cross-blur settings are retained but are not consumed by the
 * current RadianceCascades implementation.</p>
 *
 * @author Albert Beaupre
 */
public final class RadianceCascadeSettings {

    /**
     * Initial sentinel selecting probe spacing multiplied by sqrt(2)/2 as the base
     * interval in solver units. The positive-only public setter cannot restore it.
     */
    static final float AUTO_BASE_INTERVAL_LENGTH = -1f;

    private int baseProbeSpacing = 2; // Configured base-level probe spacing in solver pixels.
    private int baseRayCount = 4; // Configured directional rays per base-level probe.
    private float baseIntervalLength = AUTO_BASE_INTERVAL_LENGTH; // Configured base ray interval length in solver units.
    private int branchFactor = 2; // Configured interval growth factor between levels.
    private boolean bilinearFix = false; // Configured spatial interpolation correction during extension, merging, and resolve.
    private int maxLevels = 0; // Configured level-count limit, with zero selecting automatic coverage.
    private int internalScale = 2; // Configured integer divisor of captured scene dimensions for solving.
    private int maxCascadeTextureWidth = 8192; // Configured cap applied to both packed cascade texture dimensions.
    private float rayStep = 0.1f; // Configured retained ray-step setting, unused by the current solver.
    private float transmittanceCutoff = 0.01f; // Configured transmittance cutoff supplied to extension and merge shaders.
    private float intensity = 1f; // Configured final resolved-light multiplier.
    private boolean crossBlur = true; // Configured retained cross-blur option, unused by the current solver.
    private float opacitySimilarityThreshold = 0.1f; // Configured surface-opacity threshold used by tracing and resolving.

    /**
     * Returns the base-level probe spacing in solver pixels.
     * The initial value is two; reading it does not allocate or rebuild solver state.
     *
     * @return configured base-level probe spacing in solver pixels
     */
    public int getBaseProbeSpacing() {
        return baseProbeSpacing;
    }

    /**
     * Stores the base-level probe spacing in solver pixels.
     * This modifies configuration only and does not notify or rebuild an existing solver.
     *
     * @param baseProbeSpacing replacement value; must be positive
     * @return this settings object
     * @throws IllegalArgumentException if the stated range check fails
     */
    public RadianceCascadeSettings setBaseProbeSpacing(int baseProbeSpacing) {
        if (baseProbeSpacing <= 0) throw new IllegalArgumentException("baseProbeSpacing must be > 0");
        this.baseProbeSpacing = baseProbeSpacing;
        return this;
    }

    /**
     * Returns the directional rays per base-level probe.
     * The initial value is four; reading it does not allocate or rebuild solver state.
     *
     * @return configured directional rays per base-level probe
     */
    public int getBaseRayCount() {
        return baseRayCount;
    }

    /**
     * Stores the directional rays per base-level probe.
     * This modifies configuration only and does not notify or rebuild an existing solver.
     *
     * @param baseRayCount replacement value; must be positive
     * @return this settings object
     * @throws IllegalArgumentException if the stated range check fails
     */
    public RadianceCascadeSettings setBaseRayCount(int baseRayCount) {
        if (baseRayCount <= 0) throw new IllegalArgumentException("baseRayCount must be > 0");
        this.baseRayCount = baseRayCount;
        return this;
    }

    /**
     * Returns the base ray interval length in solver units.
     * The initial value is automatic (-1); reading it does not allocate or rebuild solver state.
     *
     * @return configured base ray interval length in solver units
     */
    public float getBaseIntervalLength() {
        return baseIntervalLength;
    }

    /**
     * Stores the base ray interval length in solver units.
     * This modifies configuration only and does not notify or rebuild an existing solver.
     * The automatic sentinel cannot be restored through this positive-only setter.
     *
     * @param baseIntervalLength replacement value; must be positive
     * @return this settings object
     * @throws IllegalArgumentException if the stated range check fails
     */
    public RadianceCascadeSettings setBaseIntervalLength(float baseIntervalLength) {
        if (baseIntervalLength <= 0f) throw new IllegalArgumentException("baseIntervalLength must be > 0");
        this.baseIntervalLength = baseIntervalLength;
        return this;
    }

    /**
     * Returns the interval growth factor between levels.
     * The initial value is two; reading it does not allocate or rebuild solver state.
     *
     * @return configured interval growth factor between levels
     */
    public int getBranchFactor() {
        return branchFactor;
    }

    /**
     * Stores the interval growth factor between levels.
     * This modifies configuration only and does not notify or rebuild an existing solver.
     *
     * @param branchFactor replacement value; must be positive; the solver additionally requires two
     * @return this settings object
     * @throws IllegalArgumentException if the stated range check fails
     */
    public RadianceCascadeSettings setBranchFactor(int branchFactor) {
        if (branchFactor <= 0) throw new IllegalArgumentException("branchFactor must be > 0");
        this.branchFactor = branchFactor;
        return this;
    }

    /**
     * Returns the spatial interpolation correction during extension, merging, and resolve.
     * The initial value is false; reading it does not allocate or rebuild solver state.
     *
     * @return configured spatial interpolation correction during extension, merging, and resolve
     */
    public boolean isBilinearFix() {
        return bilinearFix;
    }

    /**
     * Stores the spatial interpolation correction during extension, merging, and resolve.
     * This modifies configuration only and does not notify or rebuild an existing solver.
     *
     * @param bilinearFix replacement value
     * @return this settings object
     */
    public RadianceCascadeSettings setBilinearFix(boolean bilinearFix) {
        this.bilinearFix = bilinearFix;
        return this;
    }

    /**
     * Returns the level-count limit, with zero selecting automatic coverage.
     * The initial value is zero; reading it does not allocate or rebuild solver state.
     *
     * @return configured level-count limit, with zero selecting automatic coverage
     */
    public int getMaxLevels() {
        return maxLevels;
    }

    /**
     * Stores the level-count limit, with zero selecting automatic coverage.
     * This modifies configuration only and does not notify or rebuild an existing solver.
     *
     * @param maxLevels replacement value; must be nonnegative
     * @return this settings object
     * @throws IllegalArgumentException if the stated range check fails
     */
    public RadianceCascadeSettings setMaxLevels(int maxLevels) {
        if (maxLevels < 0) throw new IllegalArgumentException("maxLevels must be >= 0");
        this.maxLevels = maxLevels;
        return this;
    }

    /**
     * Returns the integer divisor of captured scene dimensions for solving.
     * The initial value is two; reading it does not allocate or rebuild solver state.
     *
     * @return configured integer divisor of captured scene dimensions for solving
     */
    public int getInternalScale() {
        return internalScale;
    }

    /**
     * Stores the integer divisor of captured scene dimensions for solving.
     * This modifies configuration only and does not notify or rebuild an existing solver.
     *
     * @param internalScale replacement value; must be positive
     * @return this settings object
     * @throws IllegalArgumentException if the stated range check fails
     */
    public RadianceCascadeSettings setInternalScale(int internalScale) {
        if (internalScale <= 0) throw new IllegalArgumentException("internalScale must be > 0");
        this.internalScale = internalScale;
        return this;
    }

    /**
     * Returns the cap applied to both packed cascade texture dimensions.
     * The initial value is 8192; reading it does not allocate or rebuild solver state.
     *
     * @return configured cap applied to both packed cascade texture dimensions
     */
    public int getMaxCascadeTextureWidth() {
        return maxCascadeTextureWidth;
    }

    /**
     * Stores the cap applied to both packed cascade texture dimensions.
     * This modifies configuration only and does not notify or rebuild an existing solver.
     *
     * @param maxCascadeTextureWidth replacement value; must be at least 64
     * @return this settings object
     * @throws IllegalArgumentException if the stated range check fails
     */
    public RadianceCascadeSettings setMaxCascadeTextureWidth(int maxCascadeTextureWidth) {
        if (maxCascadeTextureWidth < 64) throw new IllegalArgumentException("maxCascadeTextureWidth must be >= 64");
        this.maxCascadeTextureWidth = maxCascadeTextureWidth;
        return this;
    }

    /**
     * Returns the retained ray-step setting, unused by the current solver.
     * The initial value is 0.1; reading it does not allocate or rebuild solver state.
     *
     * @return configured retained ray-step setting, unused by the current solver
     */
    public float getRayStep() {
        return rayStep;
    }

    /**
     * Stores the retained ray-step setting, unused by the current solver.
     * This modifies configuration only and does not notify or rebuild an existing solver.
     *
     * @param rayStep replacement value; must be positive
     * @return this settings object
     * @throws IllegalArgumentException if the stated range check fails
     */
    public RadianceCascadeSettings setRayStep(float rayStep) {
        if (rayStep <= 0f) throw new IllegalArgumentException("rayStep must be > 0");
        this.rayStep = rayStep;
        return this;
    }

    /**
     * Returns the transmittance cutoff supplied to extension and merge shaders.
     * The initial value is 0.01; reading it does not allocate or rebuild solver state.
     *
     * @return configured transmittance cutoff supplied to extension and merge shaders
     */
    public float getTransmittanceCutoff() {
        return transmittanceCutoff;
    }

    /**
     * Stores the transmittance cutoff supplied to extension and merge shaders.
     * This modifies configuration only and does not notify or rebuild an existing solver.
     *
     * @param transmittanceCutoff replacement value; must be between zero and one
     * @return this settings object
     * @throws IllegalArgumentException if the stated range check fails
     */
    public RadianceCascadeSettings setTransmittanceCutoff(float transmittanceCutoff) {
        if (transmittanceCutoff < 0f || transmittanceCutoff > 1f)
            throw new IllegalArgumentException("transmittanceCutoff must be in [0, 1]");
        this.transmittanceCutoff = transmittanceCutoff;
        return this;
    }

    /**
     * Returns the final resolved-light multiplier.
     * The initial value is one; reading it does not allocate or rebuild solver state.
     *
     * @return configured final resolved-light multiplier
     */
    public float getIntensity() {
        return intensity;
    }

    /**
     * Stores the final resolved-light multiplier.
     * This modifies configuration only and does not notify or rebuild an existing solver.
     * The multiplier is stored without sign or finiteness validation.
     *
     * @param intensity replacement value
     * @return this settings object
     */
    public RadianceCascadeSettings setIntensity(float intensity) {
        this.intensity = intensity;
        return this;
    }

    /**
     * Returns the retained cross-blur option, unused by the current solver.
     * The initial value is true; reading it does not allocate or rebuild solver state.
     *
     * @return configured retained cross-blur option, unused by the current solver
     */
    public boolean isCrossBlur() {
        return crossBlur;
    }

    /**
     * Stores the retained cross-blur option, unused by the current solver.
     * This modifies configuration only and does not notify or rebuild an existing solver.
     *
     * @param crossBlur replacement value
     * @return this settings object
     */
    public RadianceCascadeSettings setCrossBlur(boolean crossBlur) {
        this.crossBlur = crossBlur;
        return this;
    }

    /**
     * Returns the surface-opacity threshold used by tracing and resolving.
     * The initial value is 0.1; reading it does not allocate or rebuild solver state.
     *
     * @return configured surface-opacity threshold used by tracing and resolving
     */
    public float getOpacitySimilarityThreshold() {
        return opacitySimilarityThreshold;
    }

    /**
     * Stores the surface-opacity threshold used by tracing and resolving.
     * This modifies configuration only and does not notify or rebuild an existing solver.
     *
     * @param opacitySimilarityThreshold replacement value; must be nonnegative
     * @return this settings object
     * @throws IllegalArgumentException if the stated range check fails
     */
    public RadianceCascadeSettings setOpacitySimilarityThreshold(float opacitySimilarityThreshold) {
        if (opacitySimilarityThreshold < 0f)
            throw new IllegalArgumentException("opacitySimilarityThreshold must be >= 0");
        this.opacitySimilarityThreshold = opacitySimilarityThreshold;
        return this;
    }

    /**
     * Returns the opacity threshold used for surface detection, with a floor of 0.0001.
     * This does not rewrite the configured opacity similarity value.
     *
     * @return effective surface-opacity threshold
     */
    float getHitOpacityThreshold() {
        return Math.max(0.0001f, opacitySimilarityThreshold);
    }

    /**
     * Checks the hierarchy constraints needed by the Flatland solver, including its
     * fixed branch factor of two. This is not exhaustive float validation and does not
     * allocate targets or query GPU limits.
     *
     * @throws IllegalArgumentException if a checked hierarchy or cutoff constraint fails
     */
    void validateForFlatland() {
        if (baseProbeSpacing <= 0) throw new IllegalArgumentException("baseProbeSpacing must be > 0");
        if (baseRayCount <= 0) throw new IllegalArgumentException("baseRayCount must be > 0");
        if (branchFactor != 2)
            throw new IllegalArgumentException("Flatland radiance cascades require branchFactor == 2");
        if (maxLevels < 0) throw new IllegalArgumentException("maxLevels must be >= 0");
        if (internalScale <= 0) throw new IllegalArgumentException("internalScale must be > 0");
        if (maxCascadeTextureWidth < 64) throw new IllegalArgumentException("maxCascadeTextureWidth must be >= 64");
        if (transmittanceCutoff < 0f || transmittanceCutoff > 1f)
            throw new IllegalArgumentException("transmittanceCutoff must be in [0, 1]");
    }
}
