package valthorne.graphics.animation;

import valthorne.asset.AssetLoader;
import valthorne.graphics.texture.TextureData;
import valthorne.utility.properties.PropertySet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Reads animation properties and decodes the linked image on an asset worker.
 * No OpenGL calls occur while loading. The returned data owns its decoded pixels;
 * create a player with asAnimation on the graphics thread and dispose data after
 * all players that still need its CPU data have been constructed.
 */
public final class AnimationLoader implements AssetLoader<AnimationParameters, AnimationData> {
    /**
     * Creates a stateless decoder suitable for the shared asset-loader registry.
     */
    public AnimationLoader() {
    }

    @Override
    public AnimationData load(AnimationParameters parameters) {
        try {
            PropertySet properties = PropertySet.fromText(new String(parameters.readConfig(), StandardCharsets.UTF_8));
            String image = properties.getRequired("image", String.class).value();
            if (image.isBlank()) throw new IllegalArgumentException("image cannot be blank");
            boolean flip = properties.getOrDefault("flipVertically", true).asBoolean();
            TextureData pixels = TextureData.load(parameters.readImage(image), flip);
            try {
                return new AnimationData(properties, pixels, true);
            } catch (RuntimeException | Error failure) {
                pixels.dispose();
                throw failure;
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to load animation: " + parameters.name(), failure);
        }
    }
}
