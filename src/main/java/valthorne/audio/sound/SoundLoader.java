package valthorne.audio.sound;

import valthorne.asset.AssetLoader;

/**
 * <p>
 * {@code SoundLoader} is the asset-loader bridge between generic asset loading and the
 * sound system. It accepts {@link SoundParameters}, inspects the underlying
 * {@link SoundSource}, and delegates loading to the appropriate {@link SoundData}
 * factory method.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * SoundLoader loader = new SoundLoader();
 * SoundData data = loader.load(SoundParameters.fromPath("music.ogg", "theme"));
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 26th, 2026
 */
public class SoundLoader implements AssetLoader<SoundParameters, SoundData> {

    /**
     * Loads sound data from the source defined by the supplied parameters.
     *
     * @param parameters the sound loading parameters
     * @return the loaded sound data
     */
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
