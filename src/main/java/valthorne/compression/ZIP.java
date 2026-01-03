package valthorne.compression;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The ZIP class implements the CompressionStrategy interface and provides methods
 * for compressing and decompressing data using the ZIP compression format.
 * <p>
 * This implementation uses Java's ZipOutputStream and ZipInputStream classes to
 * perform compression and decompression, respectively. The ZIP format supports
 * archiving and compression and is commonly used for packaging multiple files
 * into a single compressed archive. However, this class focuses on single-entry
 * compression where data is stored under a specific entry name.
 * <p>
 * The class is designed to compress and decompress data in memory and is particularly
 * useful for scenarios where data needs to be compressed for storage or transmission.
 * The entry name provided when creating an instance of the class is used as the
 * identifier for the compressed data in the ZIP format.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @see ZipOutputStream
 * @see ZipInputStream
 * @since May 10th, 2025
 */
public record ZIP(String entryName) implements CompressionStrategy {


    /**
     * Compresses the given byte array using the ZIP compression format.
     * The data is stored as a single ZIP entry named "data".
     *
     * @param data The input data to be compressed.
     * @return The compressed data as a byte array in ZIP format.
     * @throws RuntimeException If an error occurs during compression.
     */
    @Override
    public byte[] compress(byte[] data) {
        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             ZipOutputStream zipOut = new ZipOutputStream(byteOut)) {

            ZipEntry entry = new ZipEntry(entryName);
            zipOut.putNextEntry(entry);
            zipOut.write(data);
            zipOut.closeEntry();

            return byteOut.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to compress using ZIP algorithm", e);
        }
    }

    /**
     * Decompresses the given ZIP-compressed byte array and returns the first entry's content.
     * Assumes the ZIP archive contains a single entry named "data".
     *
     * @param data The compressed data in ZIP format.
     * @return The decompressed data as a byte array.
     * @throws RuntimeException If an error occurs during decompression or if the entry is not found.
     */
    @Override
    public byte[] decompress(byte[] data) {
        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(data);
             ZipInputStream zipIn = new ZipInputStream(byteIn);
             ByteArrayOutputStream byteOut = new ByteArrayOutputStream()) {

            ZipEntry entry = zipIn.getNextEntry();
            if (entry == null || !entryName.equals(entry.getName()))
                throw new RuntimeException("ZIP entry not found or invalid");

            byte[] buffer = new byte[1024];
            int len;
            while ((len = zipIn.read(buffer)) != -1) {
                byteOut.write(buffer, 0, len);
            }

            zipIn.closeEntry();
            return byteOut.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to decompress using ZIP algorithm", e);
        }
    }
}
