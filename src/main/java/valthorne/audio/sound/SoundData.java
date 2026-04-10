package valthorne.audio.sound;

import valthorne.audio.AudioFormat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * <p>
 * {@code SoundData} is the central immutable description of loaded sound content in
 * Valthorne. It can represent either fully buffered PCM data or a stream-backed audio
 * source depending on the format, metadata, and loader heuristics.
 * </p>
 *
 * <p>
 * Instances store decoded PCM data when the sound is buffered, or stream metadata and
 * source information when the sound should be decoded incrementally at playback time.
 * The factory methods {@link #load(byte[])} and {@link #load(String)} perform format
 * detection, metadata probing, compression classification, and buffered-versus-streamed
 * selection automatically.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * SoundData click = SoundData.load("assets/sfx/click.wav");
 * SoundData theme = SoundData.load(Files.readAllBytes(Path.of("assets/music/theme.ogg")));
 *
 * if (theme.streaming()) {
 *     try (SoundStream stream = theme.openStream()) {
 *         stream.seek(10f);
 *     }
 * }
 * }</pre>
 *
 * @param source        the original source used for stream reopening, or {@code null} for fully buffered sounds
 * @param data          the decoded PCM data for buffered sounds, or {@code null} for streamed sounds
 * @param streamOffset  the start offset used when opening a stream from the source
 * @param streamLength  the readable stream length in bytes
 * @param duration      the sound duration in seconds
 * @param channels      the number of audio channels
 * @param sampleRate    the sample rate in hertz
 * @param bitsPerSample the bits per sample
 * @param streaming     whether this sound should be played through a stream
 * @param compressed    whether the original format is compressed
 * @param format        the detected audio format
 * @author Albert Beaupre
 * @since March 26th, 2026
 */
public record SoundData(SoundSource source, ByteBuffer data, long streamOffset, long streamLength, float duration, int channels, int sampleRate, int bitsPerSample, boolean streaming, boolean compressed, AudioFormat format) {

    /**
     * Maximum PCM byte count that should still be buffered fully.
     */
    private static final long MAX_BUFFERED_PCM_BYTES = 8L * 1024L * 1024L;

    /**
     * Maximum duration that should still be buffered fully.
     */
    private static final float MAX_BUFFERED_DURATION_SECONDS = 15f;

    /**
     * Number of header bytes read during path-based format detection.
     */
    private static final int DETECT_BYTES = 64;

    /**
     * Loads sound data from an in-memory byte array.
     *
     * @param bytes the encoded sound bytes
     * @return the loaded sound data
     */
    public static SoundData load(byte[] bytes) {
        try {
            AudioFormat format = AudioFormat.detect(bytes);
            SoundDecoder decoder = format.getDecoder();

            if (decoder == null) {
                throw new IllegalArgumentException("Unsupported audio format: " + format);
            }

            SoundMetadata metadata = probeBytes(format, bytes);
            boolean compressed = isCompressed(format);

            if (shouldStream(format, metadata)) {
                return new SoundData(new SoundSource.BytesSource(bytes), null, metadata.dataOffset(), metadata.dataLength(), metadata.duration(), metadata.channels(), metadata.sampleRate(), metadata.bitsPerSample(), true, compressed, format);
            }

            return decoder.decode(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads sound data from a filesystem path.
     *
     * @param path the file path to load
     * @return the loaded sound data
     */
    public static SoundData load(String path) {
        try {
            AudioFormat format = AudioFormat.detect(readHeader(path));
            SoundDecoder decoder = format.getDecoder();

            if (decoder == null) {
                throw new IllegalArgumentException("Unsupported audio format: " + format);
            }

            SoundMetadata metadata = probePath(format, path);
            boolean compressed = isCompressed(format);

            if (shouldStream(format, metadata)) {
                return new SoundData(new SoundSource.PathSource(path), null, metadata.dataOffset(), metadata.dataLength(), metadata.duration(), metadata.channels(), metadata.sampleRate(), metadata.bitsPerSample(), true, compressed, format);
            }

            return decoder.decode(Files.readAllBytes(Path.of(path)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Opens a new decoded stream for this sound.
     *
     * @return a new sound stream
     */
    public SoundStream openStream() {
        if (!streaming) {
            throw new IllegalStateException("SoundData is not stream-backed");
        }

        return switch (format) {
            case WAV -> new WaveSoundStream(this);
            case OGG -> new OggSoundStream(this);
            case MP3 -> new Mp3SoundStream(this);
            default -> throw new IllegalStateException("Streaming is not supported for format: " + format);
        };
    }

    /**
     * Estimates the PCM byte count represented by this sound.
     *
     * @return the estimated PCM byte count, {@code -1} when insufficient data exists,
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

    /**
     * Returns whether the sound should be streamed rather than fully buffered.
     *
     * @param format   the detected audio format
     * @param metadata the probed metadata
     * @return {@code true} when streaming should be used
     */
    private static boolean shouldStream(AudioFormat format, SoundMetadata metadata) {
        if (metadata.duration() <= 0f) {
            return true;
        }

        if (metadata.duration() > MAX_BUFFERED_DURATION_SECONDS) {
            return true;
        }

        long estimatedPcmBytes = metadata.estimatedPcmBytes();
        if (estimatedPcmBytes < 0L) {
            return true;
        }

        if (format == AudioFormat.WAV) {
            return metadata.dataLength() > MAX_BUFFERED_PCM_BYTES;
        }

        return estimatedPcmBytes > MAX_BUFFERED_PCM_BYTES;
    }

    /**
     * Converts the current SoundData object into a SoundPlayer instance.
     *
     * @return a new SoundPlayer initialized with the current SoundData
     */
    public SoundPlayer asSoundPlayer() {
        return new SoundPlayer(this);
    }

    /**
     * Returns whether the detected format should be considered compressed.
     *
     * @param format the detected format
     * @return {@code true} when the format is compressed
     */
    private static boolean isCompressed(AudioFormat format) {
        return switch (format) {
            case WAV -> false;
            default -> true;
        };
    }

    /**
     * Probes metadata from an in-memory sound source.
     *
     * @param format the detected format
     * @param bytes  the encoded sound bytes
     * @return the probed sound metadata
     * @throws Exception if probing fails
     */
    private static SoundMetadata probeBytes(AudioFormat format, byte[] bytes) throws Exception {
        return switch (format) {
            case WAV -> WaveSoundDecoder.probe(bytes);
            case OGG -> OggSoundDecoder.probe(bytes);
            case MP3 -> Mp3SoundDecoder.probe(bytes);
            default -> throw new IllegalArgumentException("Unsupported audio format: " + format);
        };
    }

    /**
     * Probes metadata from a path-based sound source.
     *
     * @param format the detected format
     * @param path   the file path
     * @return the probed sound metadata
     * @throws Exception if probing fails
     */
    private static SoundMetadata probePath(AudioFormat format, String path) throws Exception {
        return switch (format) {
            case WAV -> WaveSoundDecoder.probe(path);
            case OGG -> OggSoundDecoder.probe(path);
            case MP3 -> Mp3SoundDecoder.probe(path);
            default -> throw new IllegalArgumentException("Unsupported audio format: " + format);
        };
    }

    /**
     * Reads a small header block from the supplied path for format detection.
     *
     * @param path the file path
     * @return the read header bytes
     * @throws IOException if the file cannot be read
     */
    private static byte[] readHeader(String path) throws IOException {
        byte[] header = new byte[DETECT_BYTES];

        try (InputStream input = Files.newInputStream(Path.of(path))) {
            int total = 0;
            while (total < header.length) {
                int read = input.read(header, total, header.length - total);
                if (read < 0) {
                    break;
                }
                total += read;
            }

            if (total == header.length) {
                return header;
            }

            byte[] resized = new byte[total];
            System.arraycopy(header, 0, resized, 0, total);
            return resized;
        }
    }
}
