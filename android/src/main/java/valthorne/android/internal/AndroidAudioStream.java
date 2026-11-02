package valthorne.android.internal;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import valthorne.audio.SoundData;
import valthorne.audio.SoundMetadata;
import valthorne.audio.SoundSource;
import valthorne.audio.SoundStream;

/**
 * Incremental Android MP3/Vorbis decoding into interleaved signed 16-bit PCM.
 * One audio thread owns each instance. Codec output is copied directly into the
 * player's reusable chunk, retaining partially consumed output between reads.
 * Seeking flushes the codec and discards pre-roll up to the requested PCM frame.
 * Native extractor and codec resources are released on close and failed startup.
 */
public final class AndroidAudioStream implements SoundStream {
    private final MediaExtractor extractor = new MediaExtractor(); // Native encoded-track cursor.
    private final BufferInfo info = new BufferInfo(); // Reused codec output metadata.
    private MediaCodec codec; // Owned decoder, null after close.
    private ByteBuffer pending; // Borrowed current decoder output until consumed.
    private int outputIndex = -1; // Codec output slot retained by pending.
    private int channels; // Decoded channel count.
    private int sampleRate; // Decoded samples per second.
    private float duration; // Container duration in seconds, negative if unknown.
    private boolean inputEnded; // Whether encoded end-of-stream has been queued.
    private boolean outputEnded; // Whether decoded end-of-stream has been observed.
    private long seekMicros; // Earliest decoded timestamp accepted after seeking.

    /**
     * Opens an independent decoder for the sound's retained encoded source.
     * @param data stream-backed sound description
     */
    public AndroidAudioStream(SoundData data) {
        try {
            if (data.source() instanceof SoundSource.BytesSource source)
                extractor.setDataSource(new AndroidEncodedAudio(source.bytes()));
            else if (data.source() instanceof SoundSource.PathSource source)
                extractor.setDataSource(source.path());
            else throw new IllegalArgumentException("Encoded sound source required");
            MediaFormat format = selectAudio(extractor);
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            duration = format.containsKey(MediaFormat.KEY_DURATION) ? format.getLong(MediaFormat.KEY_DURATION) * 1e-6f : -1f;
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();
        } catch (IOException | RuntimeException failure) {
            try {
                close();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw new IllegalStateException("Unable to open Android audio decoder", failure);
        }
    }

    /**
     * Selects the first audio track or rejects a container without audio.
     * @param extractor initialized extractor
     * @return selected track format
     */
    private static MediaFormat selectAudio(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                extractor.selectTrack(i);
                return format;
            }
        }
        throw new IllegalArgumentException("Container contains no audio track");
    }

    /**
     * Probes encoded metadata without allocating PCM or starting a decoder.
     * @param bytes encoded MP3 or Ogg bytes
     * @return stream format and duration
     */
    public static SoundMetadata probe(byte[] bytes) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(new AndroidEncodedAudio(bytes));
            MediaFormat format = selectAudio(extractor);
            float duration = format.containsKey(MediaFormat.KEY_DURATION) ? format.getLong(MediaFormat.KEY_DURATION) * 1e-6f : -1f;
            return new SoundMetadata(duration, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT), format.getInteger(MediaFormat.KEY_SAMPLE_RATE), 16, 0, bytes.length);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid encoded audio", failure);
        } finally {
            extractor.release();
        }
    }

    @Override
    public int channels() { return channels; }

    @Override
    public int sampleRate() { return sampleRate; }

    @Override
    public int bitsPerSample() { return 16; }

    @Override
    public float duration() { return duration; }

    @Override
    public boolean seek(float seconds) {
        if (codec == null || !Float.isFinite(seconds) || seconds < 0) return false;
        releaseOutput();
        seekMicros = (long) (seconds * 1_000_000);
        extractor.seekTo(seekMicros, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        codec.flush();
        inputEnded = outputEnded = false;
        return true;
    }

    @Override
    public int read(ByteBuffer destination) {
        if (codec == null) throw new IllegalStateException("Audio decoder closed");
        destination.clear();
        int frameSize = channels * 2;
        destination.limit(destination.capacity() - destination.capacity() % frameSize);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (destination.hasRemaining()) {
            if (pending != null && pending.hasRemaining()) {
                int count = Math.min(destination.remaining(), pending.remaining());
                int limit = pending.limit();
                pending.limit(pending.position() + count);
                destination.put(pending);
                pending.limit(limit);
                continue;
            }
            releaseOutput();
            if (outputEnded) break;
            if (System.nanoTime() > deadline) throw new IllegalStateException("Android audio decoder made no progress");
            if (!inputEnded) {
                int input = codec.dequeueInputBuffer(0);
                if (input >= 0) {
                    ByteBuffer encoded = codec.getInputBuffer(input);
                    encoded.clear();
                    int size = extractor.readSampleData(encoded, 0);
                    inputEnded = size < 0;
                    codec.queueInputBuffer(input, 0, Math.max(0, size), inputEnded ? 0 : extractor.getSampleTime(), inputEnded ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    if (!inputEnded) extractor.advance();
                }
            }
            int output = codec.dequeueOutputBuffer(info, 10_000);
            if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat format = codec.getOutputFormat();
                if (format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != channels || format.getInteger(MediaFormat.KEY_SAMPLE_RATE) != sampleRate || format.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) != AudioFormat.ENCODING_PCM_16BIT)
                    throw new UnsupportedOperationException("Decoder changed the requested PCM format");
            } else if (output >= 0) {
                outputIndex = output;
                pending = codec.getOutputBuffer(output);
                pending.position(info.offset);
                pending.limit(info.offset + info.size);
                if (seekMicros > info.presentationTimeUs) {
                    long skipFrames = (seekMicros - info.presentationTimeUs) * sampleRate / 1_000_000;
                    pending.position(pending.position() + (int) Math.min(pending.remaining(), skipFrames * frameSize));
                }
                outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            }
        }
        int count = destination.position();
        destination.flip();
        return count;
    }

    /**
     * Returns a consumed decoder output slot before acquiring another one.
     */
    private void releaseOutput() {
        if (outputIndex >= 0) codec.releaseOutputBuffer(outputIndex, false);
        outputIndex = -1;
        pending = null;
    }

    @Override
    public void close() {
        try {
            if (codec != null) {
                codec.release();
                codec = null;
            }
        } finally {
            extractor.release();
        }
    }
}
