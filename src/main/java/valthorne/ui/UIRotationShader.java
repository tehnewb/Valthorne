package valthorne.ui;

import valthorne.io.file.ValthorneFiles;
import valthorne.graphics.texture.TextureBatchShader;

import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL20.GL_MAX_TEXTURE_IMAGE_UNITS;

/**
 * Applies the UI context's accumulated rigid transform to textured UI vertices.
 * The owning root creates this shader only when rotated content is first drawn
 * and disposes it with the root. World coordinates are transformed before both
 * projection and clipping, matching NanoVG and Slug placement.
 *
 * <p>The context flushes queued texture work before changing the transform and
 * restores the caller's shader on scope exit. Unrotated UI outside such a scope
 * continues to use the normal batch shader. This shader does not own a batch,
 * textures, or transform stack and is confined to the graphics thread.</p>
 */
final class UIRotationShader extends TextureBatchShader {
    /**
     * Creates a shader matching the root batch's automatically detected sampler
     * limit. Requires the root's current OpenGL context.
     */
    UIRotationShader() {
        super(Math.min(16, glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS)), ValthorneFiles.readString("valthorne/shaders/ui/rotation.vert"), null);
    }

    /**
     * Uploads the current UI transform after queued work has been flushed.
     *
     * @param cosine combined rotation cosine
     * @param sine combined rotation sine
     * @param x combined world translation X
     * @param y combined world translation Y
     */
    void transform(float cosine, float sine, float x, float y) {
        setUniform4f("u_uiTransform", cosine, sine, x, y);
    }
}
