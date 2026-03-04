package valthorne.compression;

import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * The XZStrategy class implements the CompressionStrategy interface and provides methods
 * for compressing and decompressing data using the XZ compression algorithm.
 * <p>
 * XZ compression is an advanced general-purpose data compression algorithm
 * that provides high compression ratios. It is commonly used for compressing large files
 * and supports various compression levels. This implementation leverages Java's
 * XZCompressorOutputStream and XZCompressorInputStream classes to perform the operations.
 * <p>
 * This class is particularly useful for scenarios that require efficient compression
 * and decompression of byte array data in memory.
 * <p>
 * This class follows the typical flow of CompressionStrategy and allows easy
 * integration into systems that require multiple compression formats.
 * <p>
 * Note: Errors during compression or decompression are wrapped into RuntimeExceptions
 * for simplicity, but they are caused by underlying IOExceptions.
 * <p>
 * See also:
 * - org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
 * - org.apache.commons.compress.compressors.xz.XZCompressorInputStream
 *
 * @author Albert Beaupre
 * @since March 3rd, 2026
 */
public class XZStrategy implements CompressionStrategy {

    @Override
    public byte[] compress(byte[] data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (XZCompressorOutputStream xz = new XZCompressorOutputStream(baos)) {
                xz.write(data);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("XZ compression failed", e);
        }
    }

    @Override
    public byte[] decompress(byte[] data) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            XZCompressorInputStream xz = new XZCompressorInputStream(bais);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;

            while ((n = xz.read(buffer)) != -1)
                out.write(buffer, 0, n);

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("XZ decompression failed", e);
        }
    }
}