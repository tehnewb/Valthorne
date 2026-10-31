package valthorne.graphics.shader;

import valthorne.asset.AssetLoader;
import valthorne.utility.properties.PropertySet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads shader settings and GLSL dependencies on the asset worker. Parsing and
 * source preprocessing allocate no OpenGL resources. Compilation is deferred
 * until ShaderData.asShader or asComputeShader runs with a current context.
 */
public final class ShaderLoader implements AssetLoader<ShaderParameters, ShaderData> {
    /**
     * Creates a stateless loader for the shared Assets registry.
     */
    public ShaderLoader() {
    }

    @Override
    public ShaderData load(ShaderParameters parameters) {
        try {
            PropertySet settings = PropertySet.fromText(new String(parameters.readConfig(), StandardCharsets.UTF_8));
            return ShaderData.read(settings, parameters::readSource, parameters.sourceResolver());
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to load shader: " + parameters.name(), failure);
        }
    }
}
