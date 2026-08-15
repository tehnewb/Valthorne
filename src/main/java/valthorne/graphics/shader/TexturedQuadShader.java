package valthorne.graphics.shader;

/**
 * Shared shader base for textured quad rendering with explicit attributes and uniforms.
 *
 * <p>This contract is used by immediate quad renderers such as {@code Sprite.draw()},
 * framebuffer blits, and standalone sprite effect shaders that should work on modern
 * OpenGL core profiles without relying on fixed-function state.</p>
 *
 * @author Albert Beaupre
 * @since August 15th, 2026
 */
public class TexturedQuadShader extends Shader {

    public static final int ATTR_POSITION = 0;
    public static final int ATTR_UV = 1;
    public static final int ATTR_COLOR = 2;

    public static final String UNIFORM_MVP = "u_mvp";
    public static final String UNIFORM_TEXTURE = "u_texture";

    private static final String DEFAULT_VERTEX_SOURCE = """
            #version 330 core

            layout(location = 0) in vec2 a_position;
            layout(location = 1) in vec2 a_uv;
            layout(location = 2) in vec4 a_color;

            uniform mat4 u_mvp;

            out vec2 v_uv;
            out vec4 v_color;

            void main() {
                gl_Position = u_mvp * vec4(a_position, 0.0, 1.0);
                v_uv = a_uv;
                v_color = a_color;
            }
            """;

    private static final String DEFAULT_FRAGMENT_SOURCE = """
            #version 330 core

            uniform sampler2D u_texture;

            in vec2 v_uv;
            in vec4 v_color;

            out vec4 fragColor;

            void main() {
                fragColor = texture(u_texture, v_uv) * v_color;
            }
            """;

    public TexturedQuadShader() {
        this(null, null);
    }

    public TexturedQuadShader(String fragmentSource) {
        this(null, fragmentSource);
    }

    public TexturedQuadShader(String vertexSource, String fragmentSource) {
        super(normalizeVertexSource(vertexSource), normalizeFragmentSource(fragmentSource));
    }

    protected static String defaultVertexSource() {
        return DEFAULT_VERTEX_SOURCE;
    }

    protected static String defaultFragmentSource() {
        return DEFAULT_FRAGMENT_SOURCE;
    }

    private static String normalizeVertexSource(String vertexSource) {
        return vertexSource == null || vertexSource.isBlank() ? DEFAULT_VERTEX_SOURCE : vertexSource;
    }

    private static String normalizeFragmentSource(String fragmentSource) {
        return fragmentSource == null || fragmentSource.isBlank() ? DEFAULT_FRAGMENT_SOURCE : fragmentSource;
    }
}
