package valthorne.sound;

import valthorne.asset.AssetParameters;

/**
 * Represents the parameters used for loading sound assets in the system. This class
 * encapsulates a unique identifier for a specific sound resource.
 * <p>
 * The SoundParameters class is designed to provide a lightweight, immutable container
 * that stores the key information necessary for identifying and retrieving a sound file.
 * The key serves as the link between the identifying information and the actual audio
 * resource file.
 * <p>
 * Typically used in conjunction with a corresponding sound loader to convert the
 * provided parameters into a concrete representation of the sound asset, such as
 * {@code SoundData}.
 *
 * @see AssetParameters
 * @see SoundData
 * @see SoundLoader
 */
public record SoundParameters(String name) implements AssetParameters {

    @Override
    public String key() {
        return name;
    }
}
