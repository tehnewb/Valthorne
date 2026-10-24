package valthorne.compression;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Encodes and decodes zlib-wrapped Deflate streams using the JDK compressor.
 * Output storage grows to the actual result size, including empty input,
 * incompressible data and streams with a high expansion ratio. Each operation
 * owns one native compressor and releases it even when processing fails.
 *
 * <p>Instances have no mutable state and may be shared between threads. Decoding
 * rejects truncated, corrupt and dictionary-dependent input instead of spinning
 * without progress. Output size is limited by Java array capacity; callers must
 * bound untrusted input when an application requires a smaller memory limit.</p>
 *
 * @author Albert Beaupre
 * @see Deflater
 * @see Inflater
 * @since May 10th, 2025
 */
public class Deflate implements CompressionStrategy {
    /**
     * Reusable chunk size within each independent compression operation.
     */
    private static final int BUFFER_SIZE = 4096;

    @Override
    public byte[] compress(byte[] data) {
        Objects.requireNonNull(data, "data");
        Deflater deflater = new Deflater();
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(32, Math.min(data.length, BUFFER_SIZE)));
            byte[] buffer = new byte[BUFFER_SIZE];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                if (count == 0 && !deflater.finished())
                    throw new IllegalStateException("Deflate compressor made no progress");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            deflater.end();
        }
    }

    @Override
    public byte[] decompress(byte[] data) {
        Objects.requireNonNull(data, "data");
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(32, Math.min(data.length, BUFFER_SIZE) * 2));
            byte[] buffer = new byte[BUFFER_SIZE];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count != 0) {
                    output.write(buffer, 0, count);
                } else if (!inflater.finished()) {
                    if (inflater.needsDictionary())
                        throw new IllegalArgumentException("Deflate stream requires a preset dictionary");
                    if (inflater.needsInput())
                        throw new IllegalArgumentException("Deflate stream is truncated");
                    throw new IllegalArgumentException("Deflate decoder made no progress");
                }
            }
            return output.toByteArray();
        } catch (DataFormatException failure) {
            throw new IllegalArgumentException("Invalid Deflate stream", failure);
        } finally {
            inflater.end();
        }
    }
}
