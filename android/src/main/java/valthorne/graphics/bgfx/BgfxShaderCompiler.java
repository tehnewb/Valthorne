package valthorne.graphics.bgfx;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import valthorne.android.internal.AndroidRuntime;

/**
 * Compiles portable shader source in the pinned native Android compiler. The
 * library loads only on first source use; packaged binary shaders do not load
 * compiler code. Calls are confined to the graphics thread. Native output and
 * diagnostics are copied before returning, and stage recipes cache successful
 * bytecode across device recovery. Standard includes live in the app cache;
 * application startup does not require an executable process or network access.
 */
final class BgfxShaderCompiler {
    /**
     * Lazily extracted standard include directory, confined to the render thread.
     */
    private static Path includes;

    /**
     * Prevents constructing the compiler boundary.
     */
    private BgfxShaderCompiler() {
    }

    /**
     * Compiles an inline stage for the active Android renderer.
     * @param source validated portable source
     * @param varyings shared varying declarations, or null for engine defaults
     * @param type vertex, fragment or compute stage letter
     * @param family essl or spirv renderer profile
     * @return complete bgfx shader container
     */
    static byte[] compile(String source, String varyings, char type, String family) {
        String profile = switch (family) {
            case "essl" -> "300_es";
            case "spirv" -> "spirv";
            default -> throw new UnsupportedOperationException("No Android shader compiler profile for " + family);
        };
        try {
            if (includes == null) {
                System.loadLibrary("valthorne_shaderc");
                Path directory = Files.createDirectories(AndroidRuntime.activity().getCacheDir().toPath().resolve("valthorne-shaderc"));
                for (String name : new String[]{"bgfx_shader.sh", "bgfx_compute.sh"}) {
                    try (InputStream input = BgfxShaderCompiler.class.getResourceAsStream("/valthorne/shaderc/include/" + name)) {
                        if (input == null) throw new IOException("Missing shader compiler include: " + name);
                        Files.copy(input, directory.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                includes = directory;
            }
            if (varyings == null) {
                try (InputStream input = BgfxShaderCompiler.class.getResourceAsStream("/valthorne/shaders/bgfx/engine/varying.def.sc")) {
                    if (input == null) throw new IOException("Missing default shader varying declarations");
                    varyings = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            return compileNative(source.getBytes(StandardCharsets.UTF_8), varyings.getBytes(StandardCharsets.UTF_8), type, profile, includes.toString());
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not prepare Android shader compilation", failure);
        }
    }

    /**
     * Compiles synchronously without retaining Java arrays or native output.
     * @param source UTF-8 portable source
     * @param varyings UTF-8 shared varying declarations
     * @param type stage letter
     * @param profile validated native profile
     * @param directory standard include directory
     * @return owned Java bytecode array
     */
    private static native byte[] compileNative(byte[] source, byte[] varyings, char type, String profile, String directory);
}
