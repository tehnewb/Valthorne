package valthorne.graphics.radiance;

import valthorne.graphics.texture.Texture;

public final class RadianceCascadeLevel {

    private final int index;
    private final int probeSpacing;
    private final int rayCount;
    private final int traceCount;
    private final int probeCountX;
    private final int probeCountY;
    private final RadianceRenderTarget radianceTarget;
    private final RadianceRenderTarget traceTarget;

    RadianceCascadeLevel(int index, int probeSpacing, int rayCount, int traceCount, int probeCountX, int probeCountY) {
        this.index = index;
        this.probeSpacing = probeSpacing;
        this.rayCount = rayCount;
        this.traceCount = traceCount;
        this.probeCountX = probeCountX;
        this.probeCountY = probeCountY;
        this.radianceTarget = new RadianceRenderTarget(Math.max(1, probeCountX * rayCount), Math.max(1, probeCountY), false, false);
        this.traceTarget = new RadianceRenderTarget(Math.max(1, probeCountX * traceCount), Math.max(1, probeCountY), false, false);
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
        return radianceTarget.getWidth();
    }

    public int getTextureHeight() {
        return radianceTarget.getHeight();
    }

    public int getTextureID() {
        return radianceTarget.getTextureID();
    }

    public Texture getTexture() {
        return radianceTarget.getTexture();
    }

    int getRadianceTextureID() {
        return radianceTarget.getTextureID();
    }

    int getTraceTextureID() {
        return traceTarget.getTextureID();
    }

    Texture getTraceTexture() {
        return traceTarget.getTexture();
    }

    void dispose() {
        radianceTarget.dispose();
        traceTarget.dispose();
    }
}
