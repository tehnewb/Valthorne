package valthorne.graphics.shader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Reads bundled UTF-8 GLSL stages and templates below /valthorne/shaders/.
 * Uses classpath streams so the same paths work from development resources and
 * packaged JARs. Each call reads fresh bytes and closes its stream; source caching,
 * preprocessing, compilation, and OpenGL access belong to callers.
 * @author Albert Beaupre
 */
public final class ShaderSources {
    /**
     * Prevents construction of this stateless classpath resource utility.
     */
    private ShaderSources() {
    }

    /**
     * Reads a complete bundled shader as UTF-8, closing the resource stream even
     * when reading fails. Concatenates name with the fixed resource prefix without
     * filesystem access or path normalization.
     *
     * @param name nonnull path relative to /valthorne/shaders/, including extension
     * @return complete shader source
     * @throws NullPointerException if name is null
     * @throws IllegalStateException if the resource is missing
     * @throws UncheckedIOException if reading or closing the resource fails
     */
    public static String load(String name) {
        Objects.requireNonNull(name, "name");
        String path = "/valthorne/shaders/" + name;
        try (InputStream stream = ShaderSources.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing shader resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read shader resource: " + path, e);
        }
    }
}
