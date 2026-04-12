package valthorne.graphics.lighting;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;

/**
 * One level in the Radiance Cascades hierarchy.
 *
 * Texture layout:  width = probeCountX * raysPerProbe,  height = probeCountY
 * Each texel at (probeX * raysPerProbe + rayIdx, probeY) stores the RGBA16F
 * radiance (RGB) and end-of-interval transmittance (A) for that ray.
 */
final class CascadeLevel {

    final int textureId;
    final int probeCountX;
    final int probeCountY;
    final int raysPerProbe;
    final float spacing;
    final float intervalStart;
    final float intervalEnd;

    CascadeLevel(int sceneWidth, int sceneHeight, int levelIndex, int baseRays, float baseSpacing, float baseRange) {
        this.raysPerProbe  = baseRays << levelIndex;
        this.spacing       = baseSpacing * (1 << levelIndex);
        this.probeCountX   = Math.max(1, (int) Math.ceil(sceneWidth  / this.spacing));
        this.probeCountY   = Math.max(1, (int) Math.ceil(sceneHeight / this.spacing));
        this.intervalStart = levelIndex == 0 ? 0f : baseRange * (1 << (levelIndex - 1));
        this.intervalEnd   = baseRange * (1 << levelIndex);

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F,
                probeCountX * raysPerProbe, probeCountY,
                0, GL_RGBA, GL_FLOAT, 0L);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    void dispose() {
        glDeleteTextures(textureId);
    }
}
