package valthorne.graphics.bgfx;

import static org.lwjgl.bgfx.BGFX.*;

/**
 * Resolves compiled stage families from the actual initialized renderer. Shared
 * HLSL containers serve Direct3D 11 and 12, SPIR-V serves Vulkan, and GLSL/ESSL
 * containers serve desktop and browser raster APIs respectively.
 */
final class BgfxShaderResources {
    /**
     * Prevents constructing the classpath stage-family resolver.
     */
    private BgfxShaderResources() {
    }

    /**
     * Returns the active device's compiled shader family.
     *
     * @return classpath family directory
     */
    static String directory() {
        /*
         * Resolve the native renderer rather than assuming the operating system
         * or the API a caller requested before device initialization.
         */
        return switch (bgfx_get_caps().rendererType()) {
            case BGFX_RENDERER_TYPE_DIRECT3D11, BGFX_RENDERER_TYPE_DIRECT3D12 -> "dx11";
            case BGFX_RENDERER_TYPE_VULKAN -> "spirv";
            case BGFX_RENDERER_TYPE_OPENGL -> "glsl";
            case BGFX_RENDERER_TYPE_OPENGLES -> "essl";
            default -> throw new UnsupportedOperationException("No compiled stages for the active renderer");
        };
    }
}
