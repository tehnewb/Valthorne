package valthorne.compression;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The GZIP class implements the CompressionStrategy interface and provides methods
 * for compressing and decompressing data using the GZIP (GNU ZIP) compression algorithm.
 * <p>
 * GZIP is a widely used file compression format commonly found in various
 * file formats and protocols, including HTTP compression and compressed archive formats
 * like .gz and .tar.gz. This class uses Java's GZIPOutputStream and GZIPInputStream classes
 * to perform compression and decompression, respectively.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @see GZIPOutputStream
 * @see GZIPInputStream
 * @since May 10th, 2025
 */
public class GZIP implements CompressionStrategy {

    /**
     * Compresses the given byte array using the GZIP compression algorithm.
     *
     * @param data The input data to be compressed.
     * @return The compressed data as a byte array.
     * @throws RuntimeException If an error occurs during compression.
     */
    @Override
    public byte[] compress(byte[] data) {
        try (ByteArrayOutputStream compressed = new ByteArrayOutputStream()) {
            try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(compressed)) {
                gzipOutputStream.write(data);
            }

            return compressed.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to compress using GZip algorithm", e);
        }
    }

    /**
     * Decompresses the given byte array using the GZIP decompression algorithm.
     *
     * @param data The compressed data to be decompressed.
     * @return The decompressed data as a byte array.
     * @throws RuntimeException If an error occurs during decompression.
     */
    @Override
    public byte[] decompress(byte[] data) {
        try (ByteArrayOutputStream decompressed = new ByteArrayOutputStream()) {
            try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(data))) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = gzipInputStream.read(buffer)) != -1) {
                    decompressed.write(buffer, 0, bytesRead);
                }
            }

            return decompressed.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to decompress using GZip algorithm", e);
        }
    }
}
