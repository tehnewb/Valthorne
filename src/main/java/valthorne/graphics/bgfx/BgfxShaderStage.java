package valthorne.graphics.bgfx;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;

/**
 * Retains one packaged stage identifier or portable bgfx source string. Inline
 * stages compile lazily for each renderer family and retain the resulting bytes
 * until their owning program is disposed. Recovery on a previously used backend
 * reuses bytecode without starting a compiler. All access is render-thread confined.
 */
final class BgfxShaderStage {
    /**
     * Upper bound on external source and varying text, in UTF-16 code units.
     */
    private static final int MAX_SOURCE_LENGTH = 1_048_576;

    private final String text; // Packaged name or original portable source text.
    private final String varyings; // Shared varying declarations, or null for the engine defaults.
    private final char type; // Vertex, fragment or compute compiler stage letter.
    private final byte[][] compiled; // Per-backend inline bytecode, null for packaged stages.

    /**
     * Validates a resource identifier or retains an inline compilation recipe.
     *
     * @param text nonblank packaged identifier or portable bgfx source
     * @param varyings optional varying declarations shared by the graphics stages
     * @param type stage letter v, f or c
     */
    BgfxShaderStage(String text, String varyings, char type) {
        if (text == null) throw new NullPointerException("stage");
        if (text.isBlank()) throw new IllegalArgumentException("Shader stage must not be blank");
        if (text.length() > MAX_SOURCE_LENGTH || text.indexOf('\0') >= 0 || varyings != null && (varyings.length() > MAX_SOURCE_LENGTH || varyings.indexOf('\0') >= 0))
            throw new IllegalArgumentException("Shader text exceeds the supported length or contains NUL");
        boolean source = !text.startsWith("bgfx:") && (text.indexOf('\n') >= 0 || text.indexOf('{') >= 0 || text.startsWith("$") || text.startsWith("#"));
        if (!source) {
            if (text.startsWith("bgfx:")) text = text.substring(5);
            if (text.isBlank() || text.contains("..") || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0)
                throw new IllegalArgumentException("Expected a packaged shader stage name");
        }
        this.text = text;
        this.varyings = varyings;
        this.type = type;
        compiled = source ? new byte[5][] : null;
    }

    /**
     * Returns an identifier suitable for diagnostics without dumping source code.
     *
     * @return packaged identifier or an inline stage label
     */
    String name() {
        return compiled == null ? text : "inline " + type + " shader";
    }

    /**
     * Releases compiled CPU containers when the owning program is disposed.
     * Device release alone retains these bytes for warm recovery.
     */
    void clear() {
        if (compiled != null) Arrays.fill(compiled, null);
    }

    /**
     * Obtains the active renderer's bytecode, compiling an inline stage once per
     * family. Failed compilations are not cached; callers retain their old program.
     *
     * @return complete immutable bgfx shader container
     */
    byte[] bytes() {
        String family = BgfxShaderResources.directory();
        if (compiled != null) {
            int index = switch (family) {
                case "glsl" -> 0;
                case "spirv" -> 1;
                case "dx11" -> 2;
                case "essl" -> 3;
                case "metal" -> 4;
                default -> throw new UnsupportedOperationException("No source compiler profile for " + family);
            };
            byte[] bytes = compiled[index];
            if (bytes == null) compiled[index] = bytes = BgfxShaderCompiler.compile(text, varyings, type, family);
            return bytes;
        }
        String path = "/valthorne/bgfx/" + family + "/" + text + ".bin";
        try (InputStream stream = BgfxShaderStage.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalArgumentException("Missing compiled shader: " + path);
            return stream.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not load " + path, failure);
        }
    }
}
