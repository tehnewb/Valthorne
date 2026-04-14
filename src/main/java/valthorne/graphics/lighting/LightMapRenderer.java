package valthorne.graphics.lighting;

import valthorne.graphics.shader.Shader;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

public final class LightMapRenderer {

    private static final String vertexSource = """
            #version 330 core
            out vec2 v_uv;
            
            void main() {
                vec2 position;
                if (gl_VertexID == 0) {
                    position = vec2(-1.0, -1.0);
                } else if (gl_VertexID == 1) {
                    position = vec2(3.0, -1.0);
                } else {
                    position = vec2(-1.0, 3.0);
                }

                gl_Position = vec4(position, 0.0, 1.0);
                v_uv = position * 0.5 + 0.5;
            }
            """;

    private static final String fragmentSource = """
            #version 330 core
            in vec2 v_uv;

            uniform sampler2D u_texture;

            out vec4 fragColor;

            void main() {
                fragColor = texture(u_texture, v_uv);
            }
            """;

    // Used to additively bake the GI irradiance into the light FBO before the
    // final multiplicative composite.  Same vertex shader, strength-scaled output.
    private static final String bakeFragmentSource = """
            #version 330 core
            in vec2 v_uv;

            uniform sampler2D u_texture;
            uniform float     u_strength;

            out vec4 fragColor;

            void main() {
                fragColor = texture(u_texture, v_uv) * u_strength;
            }
            """;

    private final Shader shader;
    private final Shader bakeShader;
    private final int vaoId;

    public LightMapRenderer() {
        shader = new Shader(vertexSource, fragmentSource);
        shader.reload();

        bakeShader = new Shader(vertexSource, bakeFragmentSource);
        bakeShader.reload();

        vaoId = glGenVertexArrays();
    }

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
     * Additively blends {@code texId} into the currently bound framebuffer,
     * scaled by {@code strength}.  Call while the light FBO is bound to bake
     * GI irradiance into the light map before the final composite.
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

    public void dispose() {
        shader.dispose();
        bakeShader.dispose();
        glDeleteVertexArrays(vaoId);
    }

    private void renderFullscreenTriangle() {
        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
    }
}
