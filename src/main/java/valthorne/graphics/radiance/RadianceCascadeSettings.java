package valthorne.graphics.radiance;

public final class RadianceCascadeSettings {

    private int baseProbeSpacing = 1;
    private int baseRayCount = 1;
    private float baseIntervalLength = 1f;
    private int branchFactor = 2;
    private int maxLevels = 0;
    private float rayStep = 1f;
    private float transmittanceCutoff = 0.01f;
    private float intensity = 1f;
    private boolean crossBlur = true;
    private float opacitySimilarityThreshold = 0.1f;
    private boolean bilinearFix = true;

    public int getBaseProbeSpacing() {
        return baseProbeSpacing;
    }

    public RadianceCascadeSettings setBaseProbeSpacing(int baseProbeSpacing) {
        if (baseProbeSpacing <= 0) throw new IllegalArgumentException("baseProbeSpacing must be > 0");
        this.baseProbeSpacing = baseProbeSpacing;
        return this;
    }

    public int getBaseRayCount() {
        return baseRayCount;
    }

    public RadianceCascadeSettings setBaseRayCount(int baseRayCount) {
        if (baseRayCount <= 0) throw new IllegalArgumentException("baseRayCount must be > 0");
        this.baseRayCount = baseRayCount;
        return this;
    }

    public float getBaseIntervalLength() {
        return baseIntervalLength;
    }

    public RadianceCascadeSettings setBaseIntervalLength(float baseIntervalLength) {
        if (baseIntervalLength <= 0f) throw new IllegalArgumentException("baseIntervalLength must be > 0");
        this.baseIntervalLength = baseIntervalLength;
        return this;
    }

    public int getBranchFactor() {
        return branchFactor;
    }

    public RadianceCascadeSettings setBranchFactor(int branchFactor) {
        if (branchFactor <= 0) throw new IllegalArgumentException("branchFactor must be > 0");
        this.branchFactor = branchFactor;
        return this;
    }

    public int getMaxLevels() {
        return maxLevels;
    }

    public RadianceCascadeSettings setMaxLevels(int maxLevels) {
        if (maxLevels < 0) throw new IllegalArgumentException("maxLevels must be >= 0");
        this.maxLevels = maxLevels;
        return this;
    }

    public float getRayStep() {
        return rayStep;
    }

    public RadianceCascadeSettings setRayStep(float rayStep) {
        if (rayStep <= 0f) throw new IllegalArgumentException("rayStep must be > 0");
        this.rayStep = rayStep;
        return this;
    }

    public float getTransmittanceCutoff() {
        return transmittanceCutoff;
    }

    public RadianceCascadeSettings setTransmittanceCutoff(float transmittanceCutoff) {
        if (transmittanceCutoff < 0f || transmittanceCutoff > 1f) throw new IllegalArgumentException("transmittanceCutoff must be in [0, 1]");
        this.transmittanceCutoff = transmittanceCutoff;
        return this;
    }

    public float getIntensity() {
        return intensity;
    }

    public RadianceCascadeSettings setIntensity(float intensity) {
        this.intensity = intensity;
        return this;
    }

    public boolean isCrossBlur() {
        return crossBlur;
    }

    public RadianceCascadeSettings setCrossBlur(boolean crossBlur) {
        this.crossBlur = crossBlur;
        return this;
    }

    public float getOpacitySimilarityThreshold() {
        return opacitySimilarityThreshold;
    }

    public RadianceCascadeSettings setOpacitySimilarityThreshold(float opacitySimilarityThreshold) {
        if (opacitySimilarityThreshold < 0f) throw new IllegalArgumentException("opacitySimilarityThreshold must be >= 0");
        this.opacitySimilarityThreshold = opacitySimilarityThreshold;
        return this;
    }

    public boolean isBilinearFix() {
        return bilinearFix;
    }

    public RadianceCascadeSettings setBilinearFix(boolean bilinearFix) {
        this.bilinearFix = bilinearFix;
        return this;
    }
}
