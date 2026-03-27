package valthorne.audio.sound;

import org.lwjgl.BufferUtils;
import valthorne.audio.AudioFormat;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * <p>
 * {@code WaveSoundDecoder} loads uncompressed PCM WAV audio into Valthorne's
 * {@link SoundData} model. It supports probing both in-memory byte arrays and
 * file paths, extracts the essential stream metadata from the WAV container, and
 * can decode the PCM payload directly into a buffered {@link ByteBuffer} when a
 * fully buffered sound is required.
 * </p>
 *
 * <p>
 * This decoder currently supports only little-endian PCM WAV files with:
 * </p>
 *
 * <ul>
 *     <li>format code {@code 1} (PCM)</li>
 *     <li>16 bits per sample</li>
 * </ul>
 *
 * <p>
 * During probing, the decoder scans RIFF chunks until it finds the {@code fmt }
 * chunk and then the audio data chunk, accepting both {@code data} and
 * {@code buffer} as valid audio payload chunk names. Metadata is returned as a
 * {@link SoundMetadata} instance so higher-level code can decide whether the file
 * should be buffered or streamed.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * byte[] bytes = Files.readAllBytes(Path.of("assets/audio/click.wav"));
 *
 * SoundMetadata metadata = WaveSoundDecoder.probe(bytes);
 * SoundData sound = new WaveSoundDecoder().decode(bytes);
 *
 * SoundMetadata fileMetadata = WaveSoundDecoder.probe("assets/audio/click.wav");
 * }</pre>
 *
 * <p>
 * This example demonstrates the full expected usage of the class: probing from
 * memory, decoding from memory, and probing from a filesystem path.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 26th, 2026
 */
public class WaveSoundDecoder implements SoundDecoder {

    /**
     * <p>
     * Decodes the supplied WAV bytes into a fully buffered {@link SoundData} instance.
     * </p>
     *
     * <p>
     * The method first probes the WAV metadata so it knows where the PCM payload starts
     * and how large it is. It then copies just the audio payload into a direct
     * {@link ByteBuffer}, flips that buffer for reading, and wraps the result in a
     * non-streaming {@link SoundData} record marked as {@link AudioFormat#WAV}.
     * </p>
     *
     * @param bytes the encoded WAV bytes to decode
     * @return a fully buffered sound data object containing the PCM payload
     */
    @Override
    public SoundData decode(byte[] bytes) {
        SoundMetadata metadata = probe(bytes);
        ByteBuffer pcm = BufferUtils.createByteBuffer((int) metadata.dataLength());
        pcm.put(bytes, (int) metadata.dataOffset(), (int) metadata.dataLength());
        pcm.flip();

        return new SoundData(null, pcm, 0L, metadata.dataLength(), metadata.duration(), metadata.channels(), metadata.sampleRate(), metadata.bitsPerSample(), false, false, AudioFormat.WAV);
    }

    /**
     * <p>
     * Probes WAV metadata from an in-memory byte array.
     * </p>
     *
     * <p>
     * The byte array is wrapped in a little-endian {@link ByteBuffer} and delegated to
     * the internal probing routine.
     * </p>
     *
     * @param bytes the encoded WAV bytes to inspect
     * @return the extracted sound metadata
     */
    public static SoundMetadata probe(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        return probe(buffer, bytes.length);
    }

    /**
     * <p>
     * Probes WAV metadata from a file path.
     * </p>
     *
     * <p>
     * The method validates the RIFF and WAVE container headers, then scans the file's
     * chunks until it finds a supported {@code fmt } chunk and a matching audio data
     * chunk. The returned metadata includes channel count, sample rate, bits per sample,
     * duration, byte offset, and byte length of the audio payload.
     * </p>
     *
     * @param path the filesystem path to the WAV file
     * @return the extracted sound metadata
     * @throws Exception if the file cannot be read or the WAV structure is invalid
     */
    public static SoundMetadata probe(String path) throws Exception {
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            if (!"RIFF".equals(readString(file))) {
                throw new RuntimeException("Invalid WAV");
            }

            readUnsignedIntLE(file);

            if (!"WAVE".equals(readString(file))) {
                throw new RuntimeException("Invalid WAV");
            }

            int channels = 0;
            int sampleRate = 0;
            int bitsPerSample = 0;

            while (file.getFilePointer() < file.length()) {
                String chunk = readString(file);
                long chunkSize = readUnsignedIntLE(file);
                long chunkStart = file.getFilePointer();

                if ("fmt ".equals(chunk)) {
                    int format = readUnsignedShortLE(file);
                    channels = readUnsignedShortLE(file);
                    sampleRate = (int) readUnsignedIntLE(file);
                    readUnsignedIntLE(file);
                    readUnsignedShortLE(file);
                    bitsPerSample = readUnsignedShortLE(file);

                    if (format != 1 || bitsPerSample != 16) {
                        throw new RuntimeException("Only PCM16 WAV supported");
                    }
                } else if ("data".equals(chunk) || "buffer".equals(chunk)) {
                    if (channels <= 0 || sampleRate <= 0 || bitsPerSample <= 0) {
                        throw new RuntimeException("Invalid WAV metadata");
                    }

                    float duration = chunkSize / (float) (channels * sampleRate * (bitsPerSample / 8f));
                    return new SoundMetadata(duration, channels, sampleRate, bitsPerSample, chunkStart, chunkSize);
                }

                long nextChunk = chunkStart + chunkSize + (chunkSize & 1L);
                file.seek(nextChunk);
            }
        }

        throw new RuntimeException("No WAV audio data chunk found");
    }

    /**
     * <p>
     * Probes WAV metadata from a little-endian byte buffer.
     * </p>
     *
     * <p>
     * This method performs the same chunk scan as the path-based probe method but works
     * directly on in-memory data. It validates the RIFF and WAVE headers, extracts PCM
     * format information from {@code fmt }, and returns metadata when an audio payload
     * chunk is discovered.
     * </p>
     *
     * @param buffer the little-endian WAV data buffer
     * @param totalBytes the total number of bytes available in the source buffer
     * @return the extracted sound metadata
     */
    private static SoundMetadata probe(ByteBuffer buffer, int totalBytes) {
        if (!"RIFF".equals(readString(buffer))) {
            throw new RuntimeException("Invalid WAV");
        }

        buffer.getInt();

        if (!"WAVE".equals(readString(buffer))) {
            throw new RuntimeException("Invalid WAV");
        }

        int channels = 0;
        int sampleRate = 0;
        int bitsPerSample = 0;

        while (buffer.remaining() >= 8) {
            String chunk = readString(buffer);
            int chunkSize = buffer.getInt();
            int chunkStart = buffer.position();

            if ("fmt ".equals(chunk)) {
                int format = buffer.getShort() & 0xFFFF;
                channels = buffer.getShort() & 0xFFFF;
                sampleRate = buffer.getInt();
                buffer.getInt();
                buffer.getShort();
                bitsPerSample = buffer.getShort() & 0xFFFF;

                if (format != 1 || bitsPerSample != 16) {
                    throw new RuntimeException("Only PCM16 WAV supported");
                }
            } else if ("data".equals(chunk) || "buffer".equals(chunk)) {
                if (channels <= 0 || sampleRate <= 0 || bitsPerSample <= 0) {
                    throw new RuntimeException("Invalid WAV metadata");
                }

                float duration = chunkSize / (float) (channels * sampleRate * (bitsPerSample / 8f));
                return new SoundMetadata(duration, channels, sampleRate, bitsPerSample, chunkStart, chunkSize);
            }

            int nextChunk = chunkStart + chunkSize + (chunkSize & 1);
            if (nextChunk < 0 || nextChunk > totalBytes) {
                break;
            }
            buffer.position(nextChunk);
        }

        throw new RuntimeException("No WAV audio data chunk found");
    }

    /**
     * <p>
     * Reads the next four-byte ASCII chunk identifier from a random-access WAV file.
     * </p>
     *
     * @param file the file to read from
     * @return the four-character chunk identifier
     * @throws Exception if the read fails
     */
    private static String readString(RandomAccessFile file) throws Exception {
        byte[] bytes = new byte[4];
        file.readFully(bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    /**
     * <p>
     * Reads an unsigned 32-bit little-endian integer from a random-access WAV file.
     * </p>
     *
     * @param file the file to read from
     * @return the decoded unsigned integer as a long
     * @throws Exception if the read fails
     */
    private static long readUnsignedIntLE(RandomAccessFile file) throws Exception {
        long b0 = file.readUnsignedByte();
        long b1 = file.readUnsignedByte();
        long b2 = file.readUnsignedByte();
        long b3 = file.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    /**
     * <p>
     * Reads an unsigned 16-bit little-endian integer from a random-access WAV file.
     * </p>
     *
     * @param file the file to read from
     * @return the decoded unsigned short as an int
     * @throws Exception if the read fails
     */
    private static int readUnsignedShortLE(RandomAccessFile file) throws Exception {
        int b0 = file.readUnsignedByte();
        int b1 = file.readUnsignedByte();
        return b0 | (b1 << 8);
    }

    /**
     * <p>
     * Reads the next four-byte ASCII chunk identifier from an in-memory WAV buffer.
     * </p>
     *
     * @param buffer the buffer to read from
     * @return the four-character chunk identifier
     */
    private static String readString(ByteBuffer buffer) {
        byte[] out = new byte[4];
        buffer.get(out);
        return new String(out, StandardCharsets.US_ASCII);
    }
}
