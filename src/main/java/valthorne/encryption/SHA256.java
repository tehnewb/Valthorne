package valthorne.encryption;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The {@code SHA256Strategy} class implements the {@link EncryptionStrategy} interface
 * to provide a mechanism for hashing data using the SHA-256 algorithm.
 *
 * <p>As SHA-256 is a one-way hash function, the {@link #decrypt(byte[])} method is
 * not supported and will throw an {@link UnsupportedOperationException} if called.</p>
 *
 * <p>This class is intended for use cases where data integrity verification is required
 * rather than data encryption and decryption.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * EncryptionStrategy sha256Strategy = new SHA256Strategy();
 * byte[] hashedData = sha256Strategy.encrypt(plainTextData);
 * }</pre>
 *
 * <p>The above example shows how to use the {@code SHA256Strategy} to hash data.
 * Since hashing is a one-way operation, the {@code decrypt} method is not applicable.</p>
 *
 * @author Albert Beaupre
 * @since September 4th, 2024
 */
public class SHA256 implements EncryptionStrategy {

    /**
     * Hashes the given {@code data} using the SHA-256 algorithm.
     *
     * <p>This method converts the input byte array into a SHA-256 hash. The resulting
     * byte array represents the hash of the input data.</p>
     *
     * @param data The data to be hashed, provided as a byte array.
     * @return The SHA-256 hash of the input data as a byte array.
     */
    @Override
    public byte[] encrypt(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * The {@code decrypt} operation is not supported for SHA-256 as it is a one-way hash function.
     *
     * @param data The encrypted data to be decrypted.
     * @return This method does not return anything as it is unsupported.
     * @throws UnsupportedOperationException Always thrown as SHA-256 is a one-way hash function.
     */
    @Override
    public byte[] decrypt(byte[] data) {
        throw new UnsupportedOperationException("SHA-256 is a one-way hash function and cannot be decrypted.");
    }
}