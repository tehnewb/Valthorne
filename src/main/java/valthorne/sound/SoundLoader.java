package valthorne.sound;

import valthorne.asset.AssetLoader;

/**
 * SoundLoader is an implementation of the AssetLoader interface used for loading audio data
 * from specified sound parameters into a SoundData object. This class handles the process
 * of converting the input parameters into the corresponding sound data for playback and processing.
 * <p>
 * This class is designed to work with configuration parameters of type SoundParameters,
 * which encapsulate the necessary information to identify the audio asset to be loaded.
 * The resulting audio data is encapsulated within the SoundData object, which provides
 * encoded sound details like audio buffer, duration, format, and sample data.
 * <p>
 * The primary responsibility of this class is to leverage the {@code SoundData.load(String path)}
 * method by extracting the relevant key from the provided sound parameters and delegating the
 * actual loading process to the SoundData class.
 *
 * @see AssetLoader
 * @see SoundParameters
 * @see SoundData
 */
public class SoundLoader implements AssetLoader<SoundParameters, SoundData> {
    @Override
    public SoundData load(SoundParameters parameters) {
        return SoundData.load(parameters.key());
    }
}
