package valthorne.graphics;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import static org.lwjgl.opengl.GL11.*;

/**
 * Immutable snapshot of the current desktop OpenGL context. This is a feature
 * report, not a GPU performance rating. Capture on the thread with that context
 * current; recapture after replacing it. OpenGL ES is not a desktop GL context.
 *
 * @param vendor      driver vendor
 * @param renderer    driver renderer description
 * @param version     actual driver version (GLFW requests minimum versions)
 * @param raster      whether the built-in GLSL 330 raster path is supported
 * @param compute     whether compute, storage buffers and image operations are available
 * @param pathTracing whether the built-in GLSL 430 path is supported
 * @param filament    whether a packaged native runtime and presentation context exist
 */
public record GraphicsCapabilities(String vendor, String renderer, String version, boolean raster, boolean compute, boolean pathTracing, boolean filament) {
    /**
     * Captures capabilities without parsing driver strings or probing GL_EXTENSIONS.
     * The calling thread must have a current desktop OpenGL context.
     *
     * @return immutable snapshot of driver identity and supported engine paths
     * @throws IllegalStateException if no OpenGL capabilities are associated with the thread
     */
    public static GraphicsCapabilities current() {
        GLCapabilities gl = GL.getCapabilities();
        return new GraphicsCapabilities(glGetString(GL_VENDOR), glGetString(GL_RENDERER), glGetString(GL_VERSION), gl.OpenGL33, supportsCompute(gl), gl.OpenGL43, FilamentPlatform.supported() && gl.OpenGL41);
    }

    /**
     * Checks the operations used by ComputeShader; custom GLSL must also fit the context.
     *
     * @param gl non-null LWJGL capability table for the context being assessed
     * @return {@code true} when compute shaders, storage buffers, and image operations exist
     * @throws NullPointerException if {@code gl} is null
     */
    public static boolean supportsCompute(GLCapabilities gl) {
        return gl.OpenGL43 || (gl.GL_ARB_compute_shader && gl.GL_ARB_shader_storage_buffer_object && (gl.OpenGL42 || gl.GL_ARB_shader_image_load_store));
    }

    /**
     * Rejects contexts below the bundled desktop shaders' baseline before allocation.
     * This method has no side effects when raster rendering is supported.
     *
     * @throws UnsupportedOperationException if OpenGL 3.3 raster support is unavailable
     */
    public void requireRaster() {
        if (!raster)
            throw new UnsupportedOperationException("Valthorne's desktop renderer requires OpenGL 3.3 / GLSL 330. Detected: " + version + ". OpenGL ES requires a separate backend and is not supported yet.");
    }
}
