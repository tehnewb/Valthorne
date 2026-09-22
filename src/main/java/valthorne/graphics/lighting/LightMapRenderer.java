package valthorne.graphics.lighting;

import valthorne.graphics.shader.Shader;
import valthorne.graphics.shader.ShaderSources;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

/**
 * OpenGL helper for composing a two-dimensional light map and baking irradiance
 * into it. Draws a fullscreen triangle into the caller's current framebuffer and
 * viewport. Texture inputs are borrowed; this renderer owns only its shader
 * programs and VAO. Operations change depth, blend, program, texture, and VAO
 * state without restoring prior bindings. Create, draw, and dispose on a thread
 * with the appropriate current OpenGL context.
 *
 * @author Albert Beaupre
 */
public final class LightMapRenderer {

    /**
     * Vertex shader source generating a fullscreen triangle from vertex IDs.
     */
    private static final String vertexSource = ShaderSources.load("lighting/light-map.vert");

    /**
     * Fragment shader source sampling the light map for multiplicative composition.
     */
    private static final String fragmentSource = ShaderSources.load("lighting/light-map.frag");

    /**
     * Fragment shader source scaling irradiance for additive light-map baking.
     */
    private static final String bakeFragmentSource = ShaderSources.load("lighting/light-map-bake.frag");

    private final Shader shader; // Owned program for multiplicative light-map composition.
    private final Shader bakeShader; // Owned program for strength-scaled additive irradiance.
    private final int vaoId; // Owned empty VAO required for fullscreen triangle draws.

    /**
     * Compiles the composite and bake programs and creates an empty VAO for the
     * vertex-ID fullscreen triangle. Requires a current OpenGL context; the
     * caller must dispose this renderer after its final use.
     */
    public LightMapRenderer() {
        shader = new Shader(vertexSource, fragmentSource);
        shader.reload();

        bakeShader = new Shader(vertexSource, bakeFragmentSource);
        bakeShader.reload();

        vaoId = glGenVertexArrays();
    }

    /**
     * Multiplies the current framebuffer by the supplied light texture using
     * destination-color blending. The caller supplies the framebuffer and viewport.
     * Leaves depth testing and blending disabled, texture unit zero active and
     * unbound, and the shader and VAO unbound; previous state is not restored.
     *
     * @param textureId borrowed two-dimensional light-map texture
     */
    public void render(int textureId) {
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);

        glBlendFunc(GL_DST_COLOR, GL_ZERO);

        shader.bind();
        shader.setUniform1i("u_texture", 0);
        renderFullscreenTriangle();
        shader.unbind();

        glBindTexture(GL_TEXTURE_2D, 0);
        glDisable(GL_BLEND);
    }

    /**
     * Additively blends strength-scaled irradiance into the current framebuffer.
     * Call while the light-map framebuffer is bound, before final composition.
     * Strength is forwarded unchecked. Leaves depth testing and blending disabled,
     * texture unit zero active and unbound, and shader and VAO unbound.
     *
     * @param texId    borrowed two-dimensional irradiance texture
     * @param strength multiplier applied by the bake shader
     */
    public void bake(int texId, float strength) {
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texId);

        bakeShader.bind();
        bakeShader.setUniform1i("u_texture", 0);
        bakeShader.setUniform1f("u_strength", strength);
        renderFullscreenTriangle();
        bakeShader.unbind();

        glBindTexture(GL_TEXTURE_2D, 0);
        glDisable(GL_BLEND);
    }

    /**
     * Deletes both owned shader programs and the fullscreen VAO in the current
     * OpenGL context. Do not render afterward; repeated disposal is not guarded.
     */
    public void dispose() {
        shader.dispose();
        bakeShader.dispose();
        glDeleteVertexArrays(vaoId);
    }

    /**
     * Draws three vertex-ID-generated vertices with the owned empty VAO. The
     * caller must bind the intended program and render target; VAO zero is bound
     * after drawing instead of restoring the previous VAO.
     */
    private void renderFullscreenTriangle() {
        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
    }
}
