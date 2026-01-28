package valthorne.graphics.texture;

import valthorne.asset.AssetParameters;

/**
 * Represents the parameters used to define a texture asset in the system.
 * This record holds the necessary attributes required to identify and manage
 * a texture asset through a unique name key.
 * <p>
 * The class provides an implementation of the AssetParameters interface,
 * ensuring the ability to retrieve the unique key associated with the texture.
 *
 * @param name the unique name of the texture asset, serving as its identifier
 */
public record TextureParameters(String name) implements AssetParameters {

    @Override
    public String key() {
        return name;
    }
}