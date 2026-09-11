package valthorne.graphics.shader;

/**
 * GLSL 3.30 depth-pass shader that preserves alpha-cutout silhouettes without
 * evaluating lighting or writing a color output. Surviving fragments use normal
 * rasterized depth; the caller supplies the depth attachment, viewport, depth-test
 * and depth-write state. MeshBatch3D uses this program during shadow rendering.
 *
 * <p>Vertex inputs are position at location zero, color at location two and UV
 * at location three. The u_mvp matrix maps supplied positions to clip space.
 * Fragment alpha multiplies interpolated vertex alpha by u_materialTint alpha,
 * and additionally samples u_texture alpha only when u_hasTexture equals one.
 * Fragments at or below u_alphaCutoff are discarded, including equality.</p>
 *
 * <p>Callers must bind the program, assign its uniforms and provide compatible
 * vertex attributes before drawing. Texture selection and ownership remain with
 * the caller. Construction compiles native shader resources, so use a compatible
 * current OpenGL context and release the program through Shader's disposal lifecycle.</p>
 *
 * @author Albert Beaupre
 */
public final class DepthShader3D extends Shader {
    /**
     * Compiles and links the bundled vertex and fragment stages through Shader.
     * This creates a program immediately; it does not configure a render target,
     * upload draw-specific uniform values or submit geometry. A current OpenGL
     * context supporting the declared shader version is required, and compilation
     * or linking failures follow the base constructor's error handling.
     */
    public DepthShader3D() {super(ShaderSources.load("model3d/depth.vert"), ShaderSources.load("model3d/depth.frag"));}
}
