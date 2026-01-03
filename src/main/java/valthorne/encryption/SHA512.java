package valthorne.encryption;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The {@code SHA512} class implements the {@link EncryptionStrategy} interface
 * to provide a mechanism for hashing data using the SHA-512 algorithm.
 *
 * <p>As SHA-512 is a one-way hash function, the {@link #decrypt(byte[])} method is
 * not supported and will throw an {@link UnsupportedOperationException} if called.</p>
 *
 * <p>This class is intended for use cases where data integrity verification or
 * secure fingerprinting is required rather than reversible encryption.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * EncryptionStrategy sha512Strategy = new SHA512();
 * byte[] hashedData = sha512Strategy.encrypt(plainTextData);
 * }</pre>
 *
 * <p>The above example shows how to use the {@code SHA512} class to hash data.
 * Since hashing is a one-way operation, the {@code decrypt} method is not applicable.</p>
 *
 * @author Albert Beaupre
 * @since March 13, 2025
 */
public class SHA512 implements EncryptionStrategy {

    /**
     * Hashes the given {@code data} using the SHA-512 algorithm.
     *
     * <p>This method converts the input byte array into a SHA-512 hash. The resulting
     * byte array represents the hash of the input data, which is 64 bytes long.</p>
     *
     * @param data the data to be hashed, provided as a byte array
     * @return the SHA-512 hash of the input data as a byte array
     * @throws RuntimeException if the SHA-512 algorithm is not available
     */
    @Override
    public byte[] encrypt(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-512 algorithm not found", e);
        }
    }

    /**
     * The {@code decrypt} operation is not supported for SHA-512 as it is a one-way hash function.
     *
     * @param data the encrypted data to be decrypted
     * @return this method does not return anything as it is unsupported
     * @throws UnsupportedOperationException always thrown as SHA-512 is a one-way hash function
     */
    @Override
    public byte[] decrypt(byte[] data) {
        throw new UnsupportedOperationException("SHA-512 is a one-way hash function and cannot be decrypted.");
    }
}