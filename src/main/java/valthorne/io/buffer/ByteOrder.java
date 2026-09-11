package valthorne.io.buffer;

/**
 * Selects the ordering of bytes in multi-byte values read or written by
 * {@link DynamicByteBuffer}. The choice changes byte significance within a value;
 * it does not reverse the sequence of values or affect single-byte operations.
 *
 * @author Albert Beaupre
 */
public enum ByteOrder {
    /**
     * Big-endian order (the most significant byte first).
     */
    BIG_ENDIAN,
    /**
     * Little-endian order (the least significant byte first).
     */
    LITTLE_ENDIAN
}
