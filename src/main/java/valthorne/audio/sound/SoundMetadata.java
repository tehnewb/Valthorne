package valthorne.audio.sound;

/**
 * <p>
 * {@code SoundMetadata} stores lightweight information about a piece of audio without
 * requiring the full decoded PCM data to be retained. It is primarily used during
 * probing and stream setup so the loader can make buffering and streaming decisions.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * SoundMetadata metadata = WaveSoundDecoder.probe(bytes);
 * long estimated = metadata.estimatedPcmBytes();
 * }</pre>
 *
 * @param duration the duration in seconds, or a negative value when unknown
 * @param channels the number of audio channels
 * @param sampleRate the sample rate in hertz
 * @param bitsPerSample the number of bits per sample
 * @param dataOffset the byte offset where streamable PCM or encoded data begins
 * @param dataLength the byte length of the relevant data section
 * @author Albert Beaupre
 * @since March 26th, 2026
 */
public record SoundMetadata(float duration, int channels, int sampleRate, int bitsPerSample, long dataOffset, long dataLength) {

    /**
     * Estimates the decoded PCM byte size represented by this metadata.
     *
     * @return the estimated PCM size, {@code -1} when insufficient information is available,
     * or {@link Long#MAX_VALUE} if the estimate would overflow
     */
    public long estimatedPcmBytes() {
        if (duration <= 0f || channels <= 0 || sampleRate <= 0 || bitsPerSample <= 0) {
            return -1L;
        }

        double bytes = duration * sampleRate * channels * (bitsPerSample / 8.0);
        if (bytes >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.round(bytes);
    }
}
