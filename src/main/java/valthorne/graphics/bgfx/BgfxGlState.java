package valthorne.graphics.bgfx;

import static org.lwjgl.opengl.GL33.*;

/**
 * Reuses primitive storage to preserve the OpenGL state changed by the solid
 * bgfx pipeline. Capture and restore surround native execution, never individual
 * shapes. Texture state on unit zero is included for bgfx initialization; these
 * programs do not sample textures or use stencil operations.
 */
final class BgfxGlState {
    private final int[] scalar = new int[1]; // Reused native-query destination for individual state values.
    private final int[] viewport = new int[4]; // Captured viewport in framebuffer pixels.
    private final int[] scissor = new int[4]; // Captured scissor box.
    private final int[] colorMask = new int[4]; // Captured independent color write permissions.
    private final int[] polygonMode = new int[2]; // Captured polygon rasterization modes.
    private int program; // Previously bound shader program.
    private int vao; // Previously bound vertex array.
    private int buffer; // Previously bound array buffer.
    private int renderbuffer; // Previously bound renderbuffer.
    private int readFramebuffer; // Previously bound read framebuffer.
    private int drawFramebuffer; // Previously bound draw framebuffer.
    private int activeTexture; // Previously selected texture unit.
    private int texture; // Unit-zero 2D texture binding.
    private int sampler; // Unit-zero sampler binding.
    private int depthFunc; // Captured depth comparison.
    private int cullMode; // Captured culled-face selection.
    private int frontFace; // Captured front-face winding.
    private int srcRgb; // Captured RGB source blend factor.
    private int dstRgb; // Captured RGB destination blend factor.
    private int srcAlpha; // Captured alpha source blend factor.
    private int dstAlpha; // Captured alpha destination blend factor.
    private int equationRgb; // Captured RGB blend equation.
    private int equationAlpha; // Captured alpha blend equation.
    private boolean depth; // Captured depth-test enablement.
    private boolean depthWrite; // Captured depth-write permission.
    private boolean blend; // Captured blend enablement.
    private boolean cull; // Captured cull enablement.
    private boolean scissorEnabled; // Captured scissor enablement.
    private boolean stencil; // Captured stencil enablement.
    private boolean srgb; // Captured framebuffer sRGB conversion.
    private boolean multisample; // Captured multisample rasterization.
    private boolean alphaCoverage; // Captured sample-alpha-to-coverage enablement.
    private boolean depthClamp; // Captured clipping behavior beyond the depth range.
    private boolean polygonOffset; // Captured filled-polygon depth offset enablement.
    private boolean sampleMask; // Captured sample-mask enablement.
    private boolean lineSmooth; // Captured line antialiasing enablement modified by bgfx state setup.
    private boolean rasterizerDiscard; // Captured transform-feedback rasterization suppression.

    /**
     * Creates reusable state storage without querying a graphics context.
     */
    BgfxGlState() {
    }

    /**
     * Captures state on the current context and temporarily permits rasterization.
     * bgfx does not manage an external transform-feedback discard setting.
     */
    void capture() {
        glGetIntegerv(GL_VIEWPORT, viewport);
        glGetIntegerv(GL_SCISSOR_BOX, scissor);
        glGetIntegerv(GL_COLOR_WRITEMASK, colorMask);
        glGetIntegerv(GL_POLYGON_MODE, polygonMode);
        program = integer(GL_CURRENT_PROGRAM);
        vao = integer(GL_VERTEX_ARRAY_BINDING);
        buffer = integer(GL_ARRAY_BUFFER_BINDING);
        renderbuffer = integer(GL_RENDERBUFFER_BINDING);
        readFramebuffer = integer(GL_READ_FRAMEBUFFER_BINDING);
        drawFramebuffer = integer(GL_DRAW_FRAMEBUFFER_BINDING);
        activeTexture = integer(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        texture = integer(GL_TEXTURE_BINDING_2D);
        sampler = integer(GL_SAMPLER_BINDING);
        glActiveTexture(activeTexture);
        depthFunc = integer(GL_DEPTH_FUNC);
        cullMode = integer(GL_CULL_FACE_MODE);
        frontFace = integer(GL_FRONT_FACE);
        srcRgb = integer(GL_BLEND_SRC_RGB);
        dstRgb = integer(GL_BLEND_DST_RGB);
        srcAlpha = integer(GL_BLEND_SRC_ALPHA);
        dstAlpha = integer(GL_BLEND_DST_ALPHA);
        equationRgb = integer(GL_BLEND_EQUATION_RGB);
        equationAlpha = integer(GL_BLEND_EQUATION_ALPHA);
        depth = glIsEnabled(GL_DEPTH_TEST);
        depthWrite = integer(GL_DEPTH_WRITEMASK) != 0;
        blend = glIsEnabled(GL_BLEND);
        cull = glIsEnabled(GL_CULL_FACE);
        scissorEnabled = glIsEnabled(GL_SCISSOR_TEST);
        stencil = glIsEnabled(GL_STENCIL_TEST);
        srgb = glIsEnabled(GL_FRAMEBUFFER_SRGB);
        multisample = glIsEnabled(GL_MULTISAMPLE);
        alphaCoverage = glIsEnabled(GL_SAMPLE_ALPHA_TO_COVERAGE);
        depthClamp = glIsEnabled(GL_DEPTH_CLAMP);
        polygonOffset = glIsEnabled(GL_POLYGON_OFFSET_FILL);
        sampleMask = glIsEnabled(GL_SAMPLE_MASK);
        lineSmooth = glIsEnabled(GL_LINE_SMOOTH);
        rasterizerDiscard = glIsEnabled(GL_RASTERIZER_DISCARD);
        glDisable(GL_RASTERIZER_DISCARD);
    }

    /**
     * Queries one scalar state without constructing a temporary direct buffer.
     *
     * @param parameter OpenGL scalar state selector
     * @return current integer or boolean value
     */
    int integer(int parameter) {
        glGetIntegerv(parameter, scalar);
        return scalar[0];
    }

    /**
     * Reapplies the captured state after bgfx has executed its queued commands.
     */
    void restore() {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
        glColorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
        glPolygonMode(GL_FRONT_AND_BACK, polygonMode[0]);
        enable(GL_DEPTH_TEST, depth);
        glDepthMask(depthWrite);
        glDepthFunc(depthFunc);
        enable(GL_BLEND, blend);
        glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        glBlendEquationSeparate(equationRgb, equationAlpha);
        enable(GL_CULL_FACE, cull);
        glCullFace(cullMode);
        glFrontFace(frontFace);
        enable(GL_SCISSOR_TEST, scissorEnabled);
        enable(GL_STENCIL_TEST, stencil);
        enable(GL_FRAMEBUFFER_SRGB, srgb);
        enable(GL_MULTISAMPLE, multisample);
        enable(GL_SAMPLE_ALPHA_TO_COVERAGE, alphaCoverage);
        enable(GL_DEPTH_CLAMP, depthClamp);
        enable(GL_POLYGON_OFFSET_FILL, polygonOffset);
        enable(GL_SAMPLE_MASK, sampleMask);
        enable(GL_LINE_SMOOTH, lineSmooth);
        enable(GL_RASTERIZER_DISCARD, rasterizerDiscard);
        glUseProgram(program);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, buffer);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glBindSampler(0, sampler);
        glActiveTexture(activeTexture);
    }

    /**
     * Restores one captured capability with no temporary state object.
     *
     * @param capability OpenGL capability constant
     * @param enabled original enablement
     */
    private static void enable(int capability, boolean enabled) {
        /*
         * Direct capability writes avoid callback objects in frame execution.
         */
        if (enabled) glEnable(capability);
        else glDisable(capability);
    }
}
