package valthorne.graphics.lighting2d;

import org.lwjgl.BufferUtils;
import java.nio.ByteBuffer;
import valthorne.graphics.OpenGLStateSnapshot;

import static org.lwjgl.opengl.GL33.*;

/**
 * Selected OpenGL snapshot extending shared render state with framebuffer bindings,
 * viewport, clear color, write mask, scissor/sRGB enablement, and samplers zero/one.
 * It owns no GPU objects and does not preserve arbitrary unlisted context state.
 * Keep captured object identifiers alive until restoration.
 *
 * @author Albert Beaupre
 */
final class Lighting2DState {
    private final OpenGLStateSnapshot base = new OpenGLStateSnapshot(); // Captured shared render bindings and capabilities.
    final int draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING); // Captured draw framebuffer identifier.
    final int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING); // Captured read framebuffer identifier.
    final int[] viewport = new int[4]; // Captured viewport origin and size.
    private final float[] clear = new float[4]; // Captured RGBA clear color.
    private final boolean[] mask = new boolean[4]; // Captured per-channel color-write mask.
    private final boolean scissor = glIsEnabled(GL_SCISSOR_TEST); // Captured scissor enablement.
    private final boolean srgb = glIsEnabled(GL_FRAMEBUFFER_SRGB); // Captured framebuffer sRGB enablement.
    private final int sampler0; // Captured sampler binding on unit zero.
    private final int sampler1; // Captured sampler binding on unit one.

    /**
     * Captures supplemental context state immediately and restores the active texture
     * selector after querying sampler bindings. Use the same current context on restoration.
     */
    Lighting2DState() {
        glGetIntegerv(GL_VIEWPORT, viewport);
        glGetFloatv(GL_COLOR_CLEAR_VALUE, clear);
        ByteBuffer data = BufferUtils.createByteBuffer(4);
        glGetBooleanv(GL_COLOR_WRITEMASK, data);
        for (int i = 0; i < 4; i++) mask[i] = data.get(i) != 0;
        int active = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        sampler0 = glGetInteger(GL_SAMPLER_BINDING);
        glActiveTexture(GL_TEXTURE1);
        sampler1 = glGetInteger(GL_SAMPLER_BINDING);
        glActiveTexture(active);
    }

    /**
     * Restores captured supplemental state and then the shared render snapshot.
     * Repeated calls reapply original values; no closed flag is maintained.
     * Captured GPU objects must remain alive in the same current context.
     */
    void restore() {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        glClearColor(clear[0], clear[1], clear[2], clear[3]);
        glColorMask(mask[0], mask[1], mask[2], mask[3]);
        if (scissor) glEnable(GL_SCISSOR_TEST);
        else glDisable(GL_SCISSOR_TEST);
        if (srgb) glEnable(GL_FRAMEBUFFER_SRGB);
        else glDisable(GL_FRAMEBUFFER_SRGB);
        glBindSampler(0, sampler0);
        glBindSampler(1, sampler1);
        base.restore();
    }
}
