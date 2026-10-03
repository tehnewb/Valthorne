package valthorne.graphics;

import static org.lwjgl.opengl.GL33.*;

/**
 * Captures a selected set of OpenGL state for restoration around rendering passes.
 * Construction reads the current context immediately; {@link #restore()} writes
 * the captured values back. Use both operations on the same thread with the same
 * OpenGL context current, and restore nested snapshots in reverse order.
 *
 * <pre>{@code
 * OpenGLStateSnapshot state = new OpenGLStateSnapshot();
 * try {
 *     // Issue rendering commands that modify the state covered by this snapshot.
 * } finally {
 *     state.restore();
 * }
 * }</pre>
 *
 * <p>Covered state includes depth testing and writes, blending factors and equations,
 * face culling and winding, framebuffer sRGB enablement, shader program, vertex
 * array and array-buffer bindings, selected texture bindings, and the sampler on
 * units two and five. Texture coverage is 2D bindings on units zero through two and buffer
 * textures on units three, four and six, and the depth array on unit five. The active texture selector is restored too.</p>
 *
 * <p>This is not a complete OpenGL state snapshot. Framebuffer bindings, viewport,
 * scissor, stencil, and unlisted texture or sampler bindings remain the caller's
 * responsibility. The snapshot owns no GPU objects and does not keep captured
 * object names alive; avoid deleting those objects before restoration.</p>
 *
 * @author Albert Beaupre
 */
public final class OpenGLStateSnapshot {
    private final boolean depth = glIsEnabled(GL_DEPTH_TEST); // Captured depth-test enablement.
    private final boolean blend = glIsEnabled(GL_BLEND); // Captured blending enablement.
    private final boolean cull = glIsEnabled(GL_CULL_FACE); // Captured face-culling enablement.
    private final boolean srgb = glIsEnabled(GL_FRAMEBUFFER_SRGB); // Captured framebuffer sRGB conversion enablement.
    private final boolean depthWrite = glGetBoolean(GL_DEPTH_WRITEMASK); // Captured depth-test enablement.
    private final int depthFunc = glGetInteger(GL_DEPTH_FUNC); // Captured depth comparison function.
    private final int cullMode = glGetInteger(GL_CULL_FACE_MODE); // Captured culled-face selection.
    private final int frontFace = glGetInteger(GL_FRONT_FACE); // Captured front-face winding convention.
    private final int srcRgb = glGetInteger(GL_BLEND_SRC_RGB); // Captured RGB source blend factor.
    private final int dstRgb = glGetInteger(GL_BLEND_DST_RGB); // Captured RGB destination blend factor.
    private final int srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA); // Captured alpha source blend factor.
    private final int dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA); // Captured alpha destination blend factor.
    private final int equationRgb = glGetInteger(GL_BLEND_EQUATION_RGB); // Captured RGB blend equation.
    private final int equationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA); // Captured alpha blend equation.
    private final int program = glGetInteger(GL_CURRENT_PROGRAM); // Borrowed shader-program identifier.
    private final int vao = glGetInteger(GL_VERTEX_ARRAY_BINDING); // Borrowed vertex-array identifier.
    private final int buffer = glGetInteger(GL_ARRAY_BUFFER_BINDING); // Borrowed array-buffer identifier.
    private final int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE); // Captured active texture-unit selector.
    private final int texture0; // Captured 2D texture binding on unit zero.
    private final int texture1; // Captured 2D texture binding on unit one.
    private final int texture2; // Captured 2D texture binding on unit two.
    private final int bufferTexture3; // Captured buffer texture binding on unit three.
    private final int bufferTexture4; // Captured buffer texture binding on unit four.
    private final int arrayTexture5; // Shadow depth array binding on unit five.
    private final int bufferTexture6; // Borrowed array-buffer identifier.
    private final int sampler5; // Comparison sampler on unit five.
    private final int sampler2; // Sampler binding captured specifically for texture unit two.

    /**
     * Captures current state through field initializers and queries the selected
     * per-unit texture and sampler bindings. Temporarily selects units zero
     * through six, then returns to the original active texture unit.
     *
     * <p>A compatible OpenGL context must already be current. Construction does
     * not create GPU objects or perform drawing, and there is no deferred capture.</p>
     */
    public OpenGLStateSnapshot() {
        glActiveTexture(GL_TEXTURE0);
        texture0 = glGetInteger(GL_TEXTURE_BINDING_2D);
        glActiveTexture(GL_TEXTURE1);
        texture1 = glGetInteger(GL_TEXTURE_BINDING_2D);
        glActiveTexture(GL_TEXTURE2);
        sampler2 = glGetInteger(GL_SAMPLER_BINDING);
        texture2 = glGetInteger(GL_TEXTURE_BINDING_2D);
        glActiveTexture(GL_TEXTURE3);
        bufferTexture3 = glGetInteger(GL_TEXTURE_BINDING_BUFFER);
        glActiveTexture(GL_TEXTURE4);
        bufferTexture4 = glGetInteger(GL_TEXTURE_BINDING_BUFFER);
        glActiveTexture(GL_TEXTURE5);
        arrayTexture5 = glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY);
        sampler5 = glGetInteger(GL_SAMPLER_BINDING);
        glActiveTexture(GL_TEXTURE6);
        bufferTexture6 = glGetInteger(GL_TEXTURE_BINDING_BUFFER);
        glActiveTexture(activeTexture);
    }

    /**
     * Restores one capability's enablement on the current OpenGL context using
     * the corresponding enable or disable operation. Other capabilities are unchanged.
     *
     * @param capability the OpenGL capability constant to restore
     * @param enabled    whether that capability was enabled at capture time
     */
    private static void enable(int capability, boolean enabled) {
        /*
         * Set only the capability captured by this guard, preserving unrelated state.
         */
        if (enabled) glEnable(capability);
        else glDisable(capability);
    }

    /**
     * Restores every captured setting and binding, finishing with the original
     * active texture selector. Uncaptured state is untouched. This method does
     * not dispose the referenced GPU objects or invalidate the snapshot.
     *
     * <p>Each call reapplies the original values, so a second restoration can overwrite
     * changes made since the first restoration. Keep the original context current and captured
     * resources alive for every restoration.</p>
     */
    public void restore() {
        enable(GL_DEPTH_TEST, depth);
        enable(GL_BLEND, blend);
        enable(GL_CULL_FACE, cull);
        enable(GL_FRAMEBUFFER_SRGB, srgb);
        glDepthMask(depthWrite);
        glDepthFunc(depthFunc);
        glCullFace(cullMode);
        glFrontFace(frontFace);
        glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        glBlendEquationSeparate(equationRgb, equationAlpha);
        glUseProgram(program);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, buffer);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, texture1);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, texture2);
        glBindSampler(2, sampler2);
        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_BUFFER, bufferTexture3);
        glActiveTexture(GL_TEXTURE4);
        glBindTexture(GL_TEXTURE_BUFFER, bufferTexture4);
        glActiveTexture(GL_TEXTURE5);
        glBindTexture(GL_TEXTURE_2D_ARRAY, arrayTexture5);
        glBindSampler(5, sampler5);
        glActiveTexture(GL_TEXTURE6);
        glBindTexture(GL_TEXTURE_BUFFER, bufferTexture6);
        glActiveTexture(activeTexture);
    }
}
