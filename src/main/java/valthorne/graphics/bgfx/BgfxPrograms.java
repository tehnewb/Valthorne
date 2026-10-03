package valthorne.graphics.bgfx;

import org.lwjgl.bgfx.BGFXMemory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import static org.lwjgl.bgfx.BGFX.*;

/**
 * Loads build-packaged OpenGL shader containers and transfers their storage to
 * bgfx. Programs use predefined matrix uniforms, so no per-shape uniform handles
 * or color updates are needed. Shader objects are owned by their linked program.
 */
final class BgfxPrograms {
    /**
     * Prevents instantiation of the focused shader resource loader.
     */
    private BgfxPrograms() {
    }

    /**
     * Creates a solid-color or instanced solid-color program from bundled stages.
     *
     * @param vertex vertex resource name without its binary suffix
     * @return owned program handle
     * @throws IllegalStateException if bgfx cannot allocate a shader or program
     */
    static short create(String vertex) {
        /*
         * Fragment stages are reference counted by bgfx. Each program takes
         * ownership of the shader references it receives, including shared code.
         */
        return create(vertex, "shape.frag");
    }

    /**
     * Links specified raw vertex and fragment stages with predefined matrices.
     *
     * @param vertex packaged vertex resource name
     * @param fragment packaged fragment resource name
     * @return owned linked program
     */
    static short create(String vertex, String fragment) {
        /*
         * Destroy local stage references after linking; the program retains its
         * own references until the device releases it.
         */
        short vs = load(vertex);
        short fs = BGFX_INVALID_HANDLE;
        try {
            fs = load(fragment);
            short program = bgfx_create_program(vs, fs, false);
            if (program == BGFX_INVALID_HANDLE)
                throw new IllegalStateException("Could not create bgfx shape program: " + vertex);
            return program;
        } finally {
            bgfx_destroy_shader(vs);
            if (fs != BGFX_INVALID_HANDLE) bgfx_destroy_shader(fs);
        }
    }

    /**
     * Reads a binary once during initialization and copies it into bgfx-owned memory.
     *
     * @param name packaged shader resource name
     * @return owned shader handle
     * @throws UncheckedIOException if the classpath resource cannot be read
     */
    private static short load(String name) {
        /*
         * bgfx_alloc supplies memory with the exact native lifetime required by
         * deferred shader creation; Java resource arrays live only during setup.
         */
        String path = "/valthorne/bgfx/" + name + ".bin";
        try (InputStream stream = BgfxPrograms.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing bgfx shader: " + path);
            byte[] bytes = stream.readAllBytes();
            BGFXMemory memory = bgfx_alloc(bytes.length);
            if (memory == null) throw new IllegalStateException("Could not allocate bgfx shader memory");
            memory.data()
                    .put(bytes);
            short shader = bgfx_create_shader(memory);
            if (shader == BGFX_INVALID_HANDLE) throw new IllegalStateException("Invalid bgfx shader: " + path);
            return shader;
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not load bgfx shader: " + path, failure);
        }
    }
}
