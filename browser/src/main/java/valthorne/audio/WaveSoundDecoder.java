package valthorne.audio;

import valthorne.browser.audio.BrowserAudioStreams;

/**
 * Decodes Wave content through the browser's bounded PCM pipeline. Ordinary
 * sound loading retains the same buffered-versus-streaming thresholds as desktop.
 */
public final class WaveSoundDecoder implements SoundDecoder {
    /**
     * Creates a stateless browser codec adapter.
     */
    public WaveSoundDecoder() {
    }

    @Override
    public SoundData decode(byte[] bytes) {
        return BrowserAudioStreams.probe(new SoundSource.BytesSource(bytes));
    }

    /**
     * Reads metadata while retaining at most the bounded decode working set.
     * @param bytes encoded audio bytes
     * @return decoded stream metadata
     */
    public static SoundMetadata probe(byte[] bytes) {
        /*
         * The metadata result shares the same format validation as playback creation.
         */
        SoundData data = BrowserAudioStreams.probe(new SoundSource.BytesSource(bytes));
        return new SoundMetadata(data.duration(), data.channels(), data.sampleRate(), data.bitsPerSample(), data.streamOffset(), data.streamLength());
    }

    /**
     * Reads metadata from an application asset or HTTP stream.
     * @param path encoded sound asset
     * @return decoded stream metadata
     */
    public static SoundMetadata probe(String path) {
        /*
         * URL sources stream through a reader instead of retaining the entire response.
         */
        SoundData data = BrowserAudioStreams.probe(new SoundSource.PathSource(path));
        return new SoundMetadata(data.duration(), data.channels(), data.sampleRate(), data.bitsPerSample(), data.streamOffset(), data.streamLength());
    }
}
