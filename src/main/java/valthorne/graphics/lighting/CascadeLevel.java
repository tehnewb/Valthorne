package valthorne.graphics.lighting;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;

/**
 * One level in the Radiance Cascades hierarchy.
 *
 * Texture layout: width = probeCountX * rayGroupsPerProbe, height = probeCountY.
 * Each texel stores the average of four angularly adjacent rays:
 * RGB = radiance, A = remaining no-hit visibility after higher-cascade merging.
 */
final class CascadeLevel {

    static final int PREAVERAGE_RAY_COUNT = 4;

    final int textureId;
    final int probeCountX;
    final int probeCountY;
    final int actualRaysPerProbe;
    final int rayGroupsPerProbe;
    final float spacing;
    final float intervalStart;
    final float intervalEnd;

    CascadeLevel(int sceneWidth, int sceneHeight, int levelIndex, int baseRays, float baseSpacing, float baseRange) {
        int intervalScale = 1 << (levelIndex * 2);

        this.actualRaysPerProbe = baseRays * intervalScale;
        this.rayGroupsPerProbe = Math.max(1, this.actualRaysPerProbe / PREAVERAGE_RAY_COUNT);
        this.spacing = baseSpacing * (1 << levelIndex);
        this.probeCountX = Math.max(1, (int) Math.ceil(sceneWidth / this.spacing));
        this.probeCountY = Math.max(1, (int) Math.ceil(sceneHeight / this.spacing));
        this.intervalStart = baseRange * ((intervalScale - 1f) / 3f);
        this.intervalEnd = this.intervalStart + baseRange * intervalScale;

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F,
                probeCountX * rayGroupsPerProbe, probeCountY,
                0, GL_RGBA, GL_FLOAT, 0L);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    void dispose() {
        glDeleteTextures(textureId);
    }
}
