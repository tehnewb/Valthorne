package valthorne.compression;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * The BZIP2Strategy class implements the CompressionStrategy interface, providing
 * methods for compressing and decompressing data using the BZIP2 compression format.
 * <p>
 * BZIP2 is a high-compression algorithm effective for compressing large datasets, offering
 * higher compression ratios compared to some other algorithms like GZIP, though it may
 * require more computational resources. This class uses Apache Commons Compress libraries
 * to handle BZIP2 compression (BZip2CompressorOutputStream) and decompression
 * (BZip2CompressorInputStream).
 * <p>
 * The class is designed to process data as byte arrays, which makes it suitable for scenarios
 * where in-memory compression and decompression are required for storage or transmission.
 * <p>
 * It handles potential IOException cases during the compression and decompression processes
 * by wrapping them in a RuntimeException, providing a simpler interface while ensuring robust
 * error handling.
 *
 * @author Albert Beaupre
 * @since March 3rd, 2026
 */
public class BZIP2Strategy implements CompressionStrategy {

    /**
     * Encodes the complete input as a BZIP2 stream using the compressor's default
     * settings. The output stream is finalized before its bytes are returned. Input
     * contents remain unchanged, and each call uses independent temporary buffers.
     *
     * @param data non-null uncompressed bytes
     *
     * @return newly allocated, finalized BZIP2 payload
     * @throws RuntimeException if the compressor reports an I/O failure; the cause is retained
     */
    @Override
    public byte[] compress(byte[] data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (BZip2CompressorOutputStream bzip = new BZip2CompressorOutputStream(baos)) {
                bzip.write(data);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("BZIP2 compression failed", e);
        }
    }

    /**
     * Expands a BZIP2 payload into a new in-memory byte array. Bytes are copied
     * until the decoder reports end of input, with no configured output-size limit.
     * The supplied encoded array is not modified.
     *
     * @param data non-null BZIP2 bytes
     *
     * @return newly allocated decompressed contents
     * @throws RuntimeException if malformed data or another decoder I/O failure prevents decoding
     */
    @Override
    public byte[] decompress(byte[] data) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            BZip2CompressorInputStream bzip = new BZip2CompressorInputStream(bais);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;

            while ((n = bzip.read(buffer)) != -1)
                out.write(buffer, 0, n);

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("BZIP2 decompression failed", e);
        }
    }
}
