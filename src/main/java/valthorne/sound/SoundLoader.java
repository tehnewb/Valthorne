package valthorne.sound;

import valthorne.asset.AssetLoader;

/**
 * The SoundLoader class is responsible for loading sound data by implementing the AssetLoader interface.
 * It supports loading sound data either from a file path or directly from a byte array, based on the type
 * of SoundSource provided in the SoundParameters.
 * <p>
 * This class determines the source type dynamically and delegates the loading operation to the appropriate
 * method in the SoundData class. Unsupported or unknown source types will result in an exception being thrown.
 */
public class SoundLoader implements AssetLoader<SoundParameters, SoundData> {
    @Override
    public SoundData load(SoundParameters parameters) {
        SoundSource src = parameters.source();

        if (src instanceof SoundSource.PathSource(String path)) {
            return SoundData.load(path);
        }

        if (src instanceof SoundSource.BytesSource(byte[] bytes)) {
            return SoundData.load(bytes);
        }

        throw new IllegalStateException("Unknown SoundSource: " + src.getClass().getName());
    }
}