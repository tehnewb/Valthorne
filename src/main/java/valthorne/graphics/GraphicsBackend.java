package valthorne.graphics;

import static org.lwjgl.bgfx.BGFX.*;

/**
 * Selects an API while all engine drawing uses the same bgfx pipeline. Explicit
 * choices never silently fall back to a different API. Feature availability is
 * reported separately by GraphicsCapabilities. WEBGL identifies the browser
 * shader target; the desktop JVM launcher cannot initialize that target.
 */
public enum GraphicsBackend {
    /**
     * Vulkan with packaged SPIR-V stages.
     */
    VULKAN(BGFX_RENDERER_TYPE_VULKAN, "spirv"),
    /**
     * Desktop OpenGL owned entirely by bgfx.
     */
    OPENGL(BGFX_RENDERER_TYPE_OPENGL, "glsl"),
    /**
     * Native OpenGL ES 3 on Android through the Android platform runtime.
     */
    OPENGLES(BGFX_RENDERER_TYPE_OPENGLES, "essl"),
    /**
     * DirectX through Direct3D 11 on Windows.
     */
    DIRECTX(BGFX_RENDERER_TYPE_DIRECT3D11, "dx11"),
    /**
     * DirectX through Direct3D 12 on Windows.
     */
    DIRECTX12(BGFX_RENDERER_TYPE_DIRECT3D12, "dx11"),
    /**
     * Browser WebGL 2 through the browser runtime's bgfx WebAssembly device.
     * Launch browser exports with this backend; desktop launchers use native APIs.
     */
    WEBGL(BGFX_RENDERER_TYPE_OPENGLES, "essl");

    private final int rendererType; // Native bgfx renderer identifier.
    private final String shaderDirectory; // Packaged shader container family.

    /**
     * Stores native renderer and compiled shader-family identifiers.
     *
     * @param rendererType bgfx renderer identifier
     * @param shaderDirectory classpath shader family directory
     */
    GraphicsBackend(int rendererType, String shaderDirectory) {
        this.rendererType = rendererType;
        this.shaderDirectory = shaderDirectory;
    }

    /**
     * Returns the requested native renderer identifier.
     *
     * @return bgfx renderer identifier
     */
    public int rendererType() {
        return rendererType;
    }

    /**
     * Returns the compiled shader container family for this API.
     *
     * @return directory beneath valthorne/bgfx
     */
    public String shaderDirectory() {
        return shaderDirectory;
    }
}
