package valthorne.audio.sound;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;

/**
 * <p>
 * {@code Mp3SoundStream} provides chunked PCM decoding for MP3 data using the Java
 * sound system. It supports both file-backed and in-memory sources and can reopen
 * itself to implement seeking.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 26th, 2026
 */
public class Mp3SoundStream implements SoundStream {

    private final SoundData data; // Stream metadata and source description
    private final File file; // File-backed source when streaming from disk
    private final byte[] encodedData; // Memory-backed source bytes when streaming from memory
    private final byte[] seekScratch = new byte[32 * 1024]; // Scratch buffer used while seeking by discard

    private javax.sound.sampled.AudioFormat pcmFormat; // PCM format used after transcoding
    private AudioInputStream sourceStream; // Encoded source stream from Java Sound
    private AudioInputStream pcmStream; // Transcoded PCM stream consumed by reads
    private int channels; // Channel count of the transcoded stream
    private int sampleRate; // Sample rate of the transcoded stream
    private int bitsPerSample; // Bits per sample of the transcoded stream
    private int frameSize; // PCM frame size in bytes

    /**
     * Creates a new MP3 stream for the supplied sound data.
     *
     * @param data the stream-backed sound data
     */
    public Mp3SoundStream(SoundData data) {
        this.data = data;

        if (data.source() instanceof SoundSource.PathSource(String path)) {
            this.file = new File(path);
            this.encodedData = null;
        } else if (data.source() instanceof SoundSource.BytesSource(byte[] bytes)) {
            this.file = null;
            this.encodedData = bytes;
        } else {
            throw new IllegalStateException("MP3 stream requires a path or byte source");
        }

        reopen();
    }

    /**
     * @return the channel count produced by this stream
     */
    @Override
    public int channels() {return channels;}

    /**
     * @return the sample rate produced by this stream
     */
    @Override
    public int sampleRate() {return sampleRate;}

    /**
     * @return the bits per sample produced by this stream
     */
    @Override
    public int bitsPerSample() {return bitsPerSample;}

    /**
     * @return the total stream duration in seconds
     */
    @Override
    public float duration() {return data.duration();}

    /**
     * Seeks by reopening the MP3 stream and discarding decoded PCM until the target time is reached.
     *
     * @param seconds the target time in seconds
     * @return {@code true} if seeking succeeded
     */
    @Override
    public boolean seek(float seconds) {
        try {
            reopen();

            long targetBytes = Math.round(clamp(seconds) * sampleRate * frameSize);
            targetBytes -= targetBytes % Math.max(1, frameSize);
            discardFully(targetBytes);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads decoded PCM bytes into the supplied buffer.
     *
     * @param pcmBuffer the destination PCM buffer
     * @return the number of bytes read
     */
    @Override
    public int read(ByteBuffer pcmBuffer) {
        try {
            pcmBuffer.clear();
            byte[] bytes = new byte[pcmBuffer.remaining()];
            int total = 0;

            while (total < bytes.length) {
                int read = pcmStream.read(bytes, total, bytes.length - total);
                if (read <= 0) {
                    break;
                }
                total += read;
            }

            if (total <= 0) {
                pcmBuffer.flip();
                return 0;
            }

            total -= total % frameSize;
            if (total <= 0) {
                pcmBuffer.flip();
                return 0;
            }

            pcmBuffer.put(bytes, 0, total);
            pcmBuffer.flip();
            return total;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Closes both source and PCM streams.
     */
    @Override
    public void close() {
        closeStreams();
    }

    /**
     * Reopens the source and PCM streams from the original sound source.
     */
    private void reopen() {
        try {
            closeStreams();
            this.sourceStream = file != null ? AudioSystem.getAudioInputStream(file) : AudioSystem.getAudioInputStream(new ByteArrayInputStream(encodedData));

            javax.sound.sampled.AudioFormat sourceFormat = sourceStream.getFormat();
            this.pcmFormat = new javax.sound.sampled.AudioFormat(javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED, sourceFormat.getSampleRate(), 16, sourceFormat.getChannels(), sourceFormat.getChannels() * 2, sourceFormat.getSampleRate(), false);

            this.pcmStream = AudioSystem.getAudioInputStream(pcmFormat, sourceStream);
            javax.sound.sampled.AudioFormat actual = pcmStream.getFormat();
            this.channels = actual.getChannels();
            this.sampleRate = Math.round(actual.getSampleRate());
            this.bitsPerSample = actual.getSampleSizeInBits() > 0 ? actual.getSampleSizeInBits() : 16;
            this.frameSize = actual.getFrameSize() > 0 ? actual.getFrameSize() : Math.max(1, channels * Math.max(1, bitsPerSample / 8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Closes the current source and PCM streams if they exist.
     */
    private void closeStreams() {
        try {
            if (pcmStream != null) {
                pcmStream.close();
            }
        } catch (Exception ignored) {
        }

        try {
            if (sourceStream != null) {
                sourceStream.close();
            }
        } catch (Exception ignored) {
        }

        pcmStream = null;
        sourceStream = null;
    }

    /**
     * Discards decoded PCM bytes until the target seek position has been reached.
     *
     * @param bytes the number of bytes to discard
     * @throws Exception if stream reading fails
     */
    private void discardFully(long bytes) throws Exception {
        long remaining = bytes;

        while (remaining > 0) {
            int chunk = (int) Math.min(seekScratch.length, remaining);
            chunk -= chunk % Math.max(1, frameSize);
            if (chunk <= 0) {
                break;
            }

            int read = pcmStream.read(seekScratch, 0, chunk);
            if (read <= 0) {
                break;
            }

            read -= read % Math.max(1, frameSize);
            if (read <= 0) {
                break;
            }

            remaining -= read;
        }
    }

    /**
     * Clamps a time value into the valid stream range.
     *
     * @param seconds the time in seconds to clamp
     * @return the clamped time
     */
    private float clamp(float seconds) {
        if (data.duration() <= 0f) {
            return Math.max(0f, seconds);
        }
        return Math.max(0f, Math.min(seconds, data.duration()));
    }
}
