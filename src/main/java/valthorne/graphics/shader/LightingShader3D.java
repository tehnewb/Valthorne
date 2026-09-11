package valthorne.graphics.shader;

/**
 * Specialized forward-lighting shader using tiled point-light lists and GGX
 * material shading. Shares the world-space mesh vertex contract with Mesh3DShader.
 * The lighting pipeline interprets albedo/tint as sRGB and computes lighting in
 * linear space, with hemisphere environment, filmic presentation, and filtered
 * directional shadows.
 * <p>
 * Construction requires the current OpenGL context. MeshBatch3D and Lighting3D
 * supply material uniforms, light buffers, tile lists, and texture bindings before
 * drawing. Program lifetime follows the Shader base class.
 * </p>
 * @author Albert Beaupre
 */
public final class LightingShader3D extends Shader {
    /**
     * Bundled tiled-lighting fragment source loaded once at class initialization.
     */
    private static final String FRAGMENT = ShaderSources.load("model3d/lighting.frag");

    /**
     * Compiles the shared mesh vertex stage with the bundled tiled-lighting fragment
     * stage. Draw-specific light buffers, samplers, and uniforms are configured by
     * the rendering pipeline before use.
     */
    public LightingShader3D() {super(Mesh3DShader.vertexSource(), FRAGMENT);}
}
