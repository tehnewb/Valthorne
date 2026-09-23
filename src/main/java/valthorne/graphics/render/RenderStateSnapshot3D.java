package valthorne.graphics.render;

import static org.lwjgl.opengl.GL33.*;

/**
 * Captures a selected set of OpenGL state for restoration around 3D rendering.
 * Construction reads the current context immediately; {@link #close()} writes
 * the captured values back. Use both operations on the same thread with the same
 * OpenGL context current, and close nested snapshots in reverse order.
 *
 * <pre>{@code
 * try (RenderStateSnapshot3D state = new RenderStateSnapshot3D()) {
 *     // Issue rendering commands that modify the state covered by this snapshot.
 * }
 * }</pre>
 *
 * <p>Covered state includes depth testing and writes, blending factors and equations,
 * face culling and winding, framebuffer sRGB enablement, shader program, vertex
 * array and array-buffer bindings, selected texture bindings, and the sampler on
 * unit two. Texture coverage is 2D bindings on units zero through two and buffer
 * textures on units three and four. The active texture selector is restored too.</p>
 *
 * <p>This is not a complete OpenGL state snapshot. Framebuffer bindings, viewport,
 * scissor, stencil, and unlisted texture or sampler bindings remain the caller's
 * responsibility. The snapshot owns no GPU objects and does not keep captured
 * object names alive; avoid deleting those objects before restoration.</p>
 *
 * @author Albert Beaupre
 */
public final class RenderStateSnapshot3D implements AutoCloseable {
    private final boolean depth = glIsEnabled(GL_DEPTH_TEST), blend = glIsEnabled(GL_BLEND), cull = glIsEnabled(GL_CULL_FACE); // Captured depth-test, blend and face-culling enablement.
    private final boolean srgb = glIsEnabled(GL_FRAMEBUFFER_SRGB); // Captured framebuffer sRGB conversion enablement.
    private final boolean depthWrite = glGetBoolean(GL_DEPTH_WRITEMASK); // Captured depth-buffer write permission.
    private final int depthFunc = glGetInteger(GL_DEPTH_FUNC), cullMode = glGetInteger(GL_CULL_FACE_MODE); // Captured depth comparison and culled-face selection.
    private final int frontFace = glGetInteger(GL_FRONT_FACE); // Captured front-face winding convention.
    private final int srcRgb = glGetInteger(GL_BLEND_SRC_RGB), dstRgb = glGetInteger(GL_BLEND_DST_RGB); // RGB source and destination blend factors.
    private final int srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA); // Alpha source and destination blend factors.
    private final int equationRgb = glGetInteger(GL_BLEND_EQUATION_RGB), equationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA); // Independent RGB and alpha blend equations.
    private final int program = glGetInteger(GL_CURRENT_PROGRAM), vao = glGetInteger(GL_VERTEX_ARRAY_BINDING); // Borrowed program and vertex-array names.
    private final int buffer = glGetInteger(GL_ARRAY_BUFFER_BINDING), activeTexture = glGetInteger(GL_ACTIVE_TEXTURE); // Array-buffer name and active texture-unit selector.
    private final int texture0, texture1, texture2, bufferTexture3, bufferTexture4; // Captured 2D bindings on units 0-2 and buffer textures on units 3-4.
    private final int sampler2; // Sampler binding captured specifically for texture unit two.

    /**
     * Captures current state through field initializers and queries the selected
     * per-unit texture and sampler bindings. Temporarily selects units zero
     * through four, then returns to the original active texture unit.
     *
     * <p>A compatible OpenGL context must already be current. Construction does
     * not create GPU objects or perform drawing, and there is no deferred capture.</p>
     */
    public RenderStateSnapshot3D() {
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
        if (enabled) glEnable(capability);
        else glDisable(capability);
    }

    /**
     * Restores every captured setting and binding, finishing with the original
     * active texture selector. Uncaptured state is untouched. This method does
     * not dispose the referenced GPU objects or mark the snapshot as closed.
     *
     * <p>Each call reapplies the original values, so a second close can overwrite
     * changes made since the first. Keep the original context current and captured
     * resources alive for every restoration.</p>
     */
    @Override
    public void close() {
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
        glActiveTexture(activeTexture);
    }
}
