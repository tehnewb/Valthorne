package valthorne.compression;

import java.nio.ByteBuffer;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * The Deflate class implements the CompressionStrategy interface and provides methods
 * for compressing and decompressing data using the Deflate compression algorithm.
 * <p>
 * Deflate is a widely used lossless data compression algorithm supported by
 * various file formats and protocols, including ZIP files and HTTP compression. This class
 * uses the java.util.zip.Deflater and java.util.zip.Inflater classes to perform compression
 * and decompression, respectively.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @see Deflater
 * @see Inflater
 * @since May 10th, 2025
 */
public class Deflate implements CompressionStrategy {

    /**
     * Compresses the given byte array using the Deflate compression algorithm.
     *
     * @param data The input data to be compressed.
     * @return The compressed data as a byte array.
     * @throws RuntimeException If an error occurs during compression.
     */
    @Override
    public byte[] compress(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();

        // Allocate a ByteBuffer with an initial capacity of half the input data length.
        ByteBuffer compressedBuffer = ByteBuffer.allocate(data.length / 2);

        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int compressedBytes = deflater.deflate(buffer);

            // Ensure there is enough space in the ByteBuffer
            if (compressedBuffer.remaining() < compressedBytes) {
                ByteBuffer newBuffer = ByteBuffer.allocate(compressedBuffer.capacity() * 2);
                compressedBuffer.flip();
                newBuffer.put(compressedBuffer);
                compressedBuffer = newBuffer;
            }

            compressedBuffer.put(buffer, 0, compressedBytes);
        }
        deflater.end();

        compressedBuffer.flip();
        byte[] compressedData = new byte[compressedBuffer.remaining()];
        compressedBuffer.get(compressedData);
        return compressedData;
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
        Inflater inflater = new Inflater();
        inflater.setInput(data);

        try {
            // Allocate a ByteBuffer with an initial capacity of twice the input data length.
            ByteBuffer decompressedBuffer = ByteBuffer.allocate(data.length * 2);

            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                int decompressedBytes = inflater.inflate(buffer);

                // Ensure there is enough space in the ByteBuffer
                if (decompressedBuffer.remaining() < decompressedBytes) {
                    ByteBuffer newBuffer = ByteBuffer.allocate(decompressedBuffer.capacity() * 2);
                    decompressedBuffer.flip();
                    newBuffer.put(decompressedBuffer);
                    decompressedBuffer = newBuffer;
                }

                decompressedBuffer.put(buffer, 0, decompressedBytes);
            }
            inflater.end();

            decompressedBuffer.flip();
            byte[] decompressedData = new byte[decompressedBuffer.remaining()];
            decompressedBuffer.get(decompressedData);
            return decompressedData;
        } catch (Exception e) {
            throw new RuntimeException("Failed to decompress using Deflate algorithm", e);
        }
    }
}
