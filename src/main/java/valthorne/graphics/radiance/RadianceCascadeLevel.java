package valthorne.graphics.radiance;

import valthorne.graphics.texture.Texture;

public final class RadianceCascadeLevel {

    private final int index;
    private final int probeSpacing;
    private final int rayCount;
    private final int raySide;
    private final int traceCount;
    private final int probeCountX;
    private final int probeCountY;
    private final int textureWidth;
    private final int textureHeight;
    private final int extensionPassCount;
    private final float intervalStart;
    private final float intervalLength;
    private final RadianceRenderTarget intervalTarget;
    private final RadianceRenderTarget scratchTarget;
    private final RadianceRenderTarget mergedTarget;

    private boolean intervalUsesScratch;

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

    public int getIndex() {
        return index;
    }

    public int getProbeSpacing() {
        return probeSpacing;
    }

    public int getRayCount() {
        return rayCount;
    }

    public int getRaySide() {
        return raySide;
    }

    public int getTraceCount() {
        return traceCount;
    }

    public int getProbeCountX() {
        return probeCountX;
    }

    public int getProbeCountY() {
        return probeCountY;
    }

    public int getTextureWidth() {
        return textureWidth;
    }

    public int getTextureHeight() {
        return textureHeight;
    }

    public int getExtensionPassCount() {
        return extensionPassCount;
    }

    public float getIntervalStart() {
        return intervalStart;
    }

    public float getIntervalLength() {
        return intervalLength;
    }

    public int getTextureID() {
        return mergedTarget.getTextureID();
    }

    public Texture getTexture() {
        return mergedTarget.getTexture();
    }

    void resetIntervalBuild() {
        intervalUsesScratch = false;
    }

    int getCurrentIntervalTextureID() {
        return (intervalUsesScratch ? scratchTarget : intervalTarget).getTextureID();
    }

    int getAlternateIntervalTextureID() {
        return (intervalUsesScratch ? intervalTarget : scratchTarget).getTextureID();
    }

    void swapIntervalTargets() {
        intervalUsesScratch = !intervalUsesScratch;
    }

    int getMergedTextureID() {
        return mergedTarget.getTextureID();
    }

    void dispose() {
        intervalTarget.dispose();
        scratchTarget.dispose();
        mergedTarget.dispose();
    }
}
