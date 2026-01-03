package valthorne.compression;

/**
 * The `CompressionStrategy` interface defines the contract for classes that implement various compression
 * and decompression algorithms. Classes implementing this interface should provide methods to encode (compress)
 * and decode (decompress) data, typically represented as byte arrays.
 * <p>
 * Implementations of this interface can be used to compress and decompress data in a consistent manner,
 * allowing for flexibility in choosing different compression algorithms while maintaining a common interface.
 * This is particularly useful when dealing with various data storage or transmission scenarios where different
 * compression techniques may be more suitable.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @since 1.0
 */
public interface CompressionStrategy {

    /**
     * A predefined instance of the GZIP class, which implements the CompressionStrategy
     * interface for performing compression and decompression using the GZIP algorithm.
     */
    GZIP GZIP = new GZIP();

    /**
     * A predefined instance of the Deflate class, which implements the CompressionStrategy
     * interface for performing data compression and decompression using the Deflate algorithm.
     */
    Deflate DEFLATE = new Deflate();

    /**
     * Creates a new instance of the ZIP class representing a compression strategy
     * that uses the ZIP format. The provided entry name is used as the identifier
     * for the compressed data entry.
     *
     * @param entryName The name of the entry to be used in the ZIP-compressed data.
     * @return A new ZIP instance configured with the specified entry name.
     */
    static ZIP ZIP(String entryName) {
        return new ZIP(entryName);
    }

    /**
     * Compresses the input data represented as a byte array.
     *
     * @param data The input data to be compressed.
     * @return A byte array containing the compressed data.
     */
    byte[] compress(byte[] data);

    /**
     * Decompresses the input data represented as a byte array.
     *
     * @param data The input data to be decompressed.
     * @return A byte array containing the decompressed data.
     */
    byte[] decompress(byte[] data);
}
