package valthorne.graphics.particle;

import valthorne.asset.AssetLoader;
import valthorne.graphics.texture.TextureData;
import valthorne.utility.properties.PropertySet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads UTF-8 PropertySet particle configuration without constructing a GPU system.
 * An optional linked image is decoded and owned by the data; imageID resolvers
 * instead lend cache-owned pixels. Config-only requests need no image dependency.
 */
public final class ParticleLoader implements AssetLoader<ParticleParameters, ParticleData> {
    /**
     * Creates a stateless loader for the shared asset registry.
     */
    public ParticleLoader() {
    }

    @Override
    public ParticleData load(ParticleParameters parameters) {
        try {
            PropertySet properties = PropertySet.fromText(new String(parameters.readConfig(), StandardCharsets.UTF_8));
            if (parameters.imageResolver() != null) return ParticleData.load(properties, parameters.imageResolver());
            if (properties.contains("imageID")) throw new IllegalArgumentException("imageID requires an image resolver");
            if (!properties.contains("image")) return ParticleData.load(properties);
            String image = properties.getRequired("image", String.class).value();
            if (image.isBlank()) throw new IllegalArgumentException("image cannot be blank");
            TextureData pixels = TextureData.load(parameters.readImage(image), properties.getOrDefault("flipVertically", true).asBoolean());
            try {
                return new ParticleData(properties, pixels, true);
            } catch (RuntimeException | Error failure) {
                pixels.dispose();
                throw failure;
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to load particles: " + parameters.name(), failure);
        }
    }
}
