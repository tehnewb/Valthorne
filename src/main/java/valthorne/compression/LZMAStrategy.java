package valthorne.compression;

import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * The LZMAStrategy class implements the CompressionStrategy interface and provides
 * methods for compressing and decompressing data using the LZMA (Lempel–Ziv–Markov chain algorithm) compression algorithm.
 * <p>
 * This implementation leverages the Apache Commons Compress library to handle LZMA compression and decompression.
 * LZMA is known for its high compression ratio and is commonly used for large-scale data compression needs
 * such as software distribution and archiving.
 * <p>
 * Compression is performed via an LZMACompressorOutputStream, whereas decompression utilizes an LZMACompressorInputStream.
 * <p>
 * This class is designed to process data represented as byte arrays and is suitable for scenarios where
 * efficient lossless compression and decompression operations are required.
 *
 * @author Albert Beaupre
 * @see LZMACompressorOutputStream
 * @see LZMACompressorInputStream
 * @since March 3rd, 2026
 */
public class LZMAStrategy implements CompressionStrategy {

    /**
     * Encodes the complete input as a LZMA stream using the compressor's default
     * settings. The output stream is finalized before its bytes are returned. Input
     * contents remain unchanged, and each call uses independent temporary buffers.
     *
     * @param data non-null uncompressed bytes
     *
     * @return newly allocated, finalized LZMA payload
     * @throws RuntimeException if the compressor reports an I/O failure; the cause is retained
     */
    @Override
    public byte[] compress(byte[] data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (LZMACompressorOutputStream lzma = new LZMACompressorOutputStream(baos)) {
                lzma.write(data);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("LZMA compression failed", e);
        }
    }

    /**
     * Expands a LZMA payload into a new in-memory byte array. Bytes are copied
     * until the decoder reports end of input, with no configured output-size limit.
     * The supplied encoded array is not modified.
     *
     * @param data non-null LZMA bytes
     *
     * @return newly allocated decompressed contents
     * @throws RuntimeException if malformed data or another decoder I/O failure prevents decoding
     */
    @Override
    public byte[] decompress(byte[] data) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            LZMACompressorInputStream lzma = new LZMACompressorInputStream(bais);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;

            while ((n = lzma.read(buffer)) != -1)
                out.write(buffer, 0, n);

            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("LZMA decompression failed", e);
        }
    }
}
