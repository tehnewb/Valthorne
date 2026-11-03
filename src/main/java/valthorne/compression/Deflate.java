package valthorne.compression;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * The Deflate class implements the CompressionStrategy interface and provides methods
 * for compressing and decompressing data using the Deflate compression algorithm.
 * <p>
 * Deflate is a widely used lossless data compression algorithm supported by
 * various file formats and protocols, including ZIP files and HTTP compression. This class
 * uses owned zlib streams to perform compression and decompression. Standard
 * stream buffering handles empty, small, and incompressible inputs without
 * manual capacity calculations. Every call releases its native codec state,
 * including failure paths; decompression rejects truncated or malformed input.
 *
 * @author Albert Beaupre
 * @see DeflaterOutputStream
 * @see InflaterInputStream
 * @since May 10th, 2025
 */
public class Deflate implements CompressionStrategy {

    /**
     * Creates a stateless zlib codec; individual calls own their stream resources.
     */
    public Deflate() {
    }

    /**
     * Compresses the given byte array using the Deflate compression algorithm.
     *
     * @param data The input data to be compressed.
     * @return The compressed data as a byte array.
     * @throws RuntimeException If an error occurs during compression.
     */
    @Override
    public byte[] compress(byte[] data) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DeflaterOutputStream stream = new DeflaterOutputStream(output)) {
            stream.write(data);
            stream.finish();
            return output.toByteArray();
        } catch (IOException failure) {
            throw new RuntimeException("Failed to compress using Deflate algorithm", failure);
        }
    }

    /**
     * Decompresses the given byte array using the Deflate decompression algorithm.
     *
     * @param data The compressed data to be decompressed.
     * @return The decompressed data as a byte array.
     * @throws RuntimeException If an error occurs during decompression.
     */
    @Override
    public byte[] decompress(byte[] data) {
        try (Inflater inflater = new Inflater(); InflaterInputStream stream = new InflaterInputStream(new ByteArrayInputStream(data), inflater)) {
            byte[] decoded = stream.readAllBytes();
            if (!inflater.finished()) throw new IOException("Incomplete zlib stream or unsupported preset dictionary");
            return decoded;
        } catch (IOException failure) {
            throw new RuntimeException("Failed to decompress using Deflate algorithm", failure);
        }
    }
}
