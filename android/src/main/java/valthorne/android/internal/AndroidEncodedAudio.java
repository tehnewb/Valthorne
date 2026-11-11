package valthorne.android.internal;

import android.media.MediaDataSource;
import java.util.Objects;

/**
 * Borrowed immutable encoded audio exposed to Android's seekable media extractor.
 * Reads copy only the requested compressed range; closing releases no caller data.
 */
final class AndroidEncodedAudio extends MediaDataSource {
    private final byte[] bytes; // Borrowed encoded payload retained for the decoder lifetime.

    /**
     * Retains the encoded payload without duplicating potentially large music data.
     * @param bytes immutable encoded audio
     */
    AndroidEncodedAudio(byte[] bytes) { this.bytes = Objects.requireNonNull(bytes); }

    @Override
    public int readAt(long position, byte[] destination, int offset, int size) {
        Objects.checkFromIndexSize(offset, size, destination.length);
        if (position < 0) throw new IllegalArgumentException("Negative audio position");
        if (size == 0) return 0;
        if (position >= bytes.length) return -1;
        int count = (int) Math.min(size, bytes.length - position);
        System.arraycopy(bytes, (int) position, destination, offset, count);
        return count;
    }

    @Override
    public long getSize() { return bytes.length; }

    @Override
    public void close() { }
}
