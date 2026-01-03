package valthorne.encryption;

/**
 * The {@code EncryptionStrategy} interface defines a contract for implementing
 * both asymmetric and symmetric encryption and decryption strategies. Classes
 * that implement this interface are expected to provide mechanisms to securely
 * encrypt and decrypt data using the appropriate cryptographic algorithms.
 *
 * <p>The interface is designed to be flexible and can be implemented by classes
 * that handle different encryption techniques, such as AES for symmetric key
 * encryption or RSA for asymmetric key encryption. The interface defines two
 * essential methods: {@link #encrypt(byte[])} for encryption and {@link #decrypt(byte[])}
 * for decryption.
 *
 * <p>This interface does not define the specific algorithm or key management
 * processes, leaving those details to the implementing classes. It is recommended
 * that implementations provide robust error handling and security features, such as
 * preventing unauthorized access to the keys used for encryption and decryption.
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * EncryptionStrategy encryptionStrategy = new AES(secretKey);
 * byte[] encryptedData = encryptionStrategy.encrypt(plainTextData);
 * byte[] decryptedData = encryptionStrategy.decrypt(encryptedData);
 * }</pre>
 *
 * <p>The above example shows how an implementation of the {@code EncryptionStrategy}
 * interface can be used to encrypt and decrypt data. In this case, the {@code AES}
 * class, which implements the interface, is instantiated and used for symmetric encryption.
 *
 * <p><strong>Thread Safety:</strong></p>
 * <p>Implementations of this interface are not inherently thread-safe. It is the
 * responsibility of the implementing class to ensure thread safety if instances
 * are to be used concurrently across multiple threads.</p>
 *
 * <p><strong>Implementing Classes:</strong></p>
 * <p>Classes that implement this interface must ensure that they can handle
 * encryption and decryption operations in a secure manner. Example implementations
 * include:</p>
 * <ul>
 *   <li>{@code AES} - An implementation that uses the AES (Advanced Encryption Standard) algorithm.</li>
 *   <li>{@code RSA} - An implementation that uses the RSA (Rivest-Shamir-Adleman) algorithm.</li>
 *   <li>{@code ChaCha20Poly1305} - An implementation that uses the ChaCha20 stream cipher with Poly1305 authentication.</li>
 *   <li>{@code ECC} - An implementation that uses Elliptic Curve Integrated Encryption Scheme (ECIES).</li>
 *   <li>{@code AESGCM} - An implementation that uses AES in Galois/Counter Mode for authenticated encryption.</li>
 *   <li>{@code Blowfish} - An implementation that uses the Blowfish symmetric-key block cipher.</li>
 *   <li>{@code SHA256} - An implementation that uses the SHA-256 hash function (one-way).</li>
 *   <li>{@code SHA512} - An implementation that uses the SHA-512 hash function (one-way).</li>
 * </ul>
 *
 * @author Albert Beaupre
 * @see java.security.Key
 * @see javax.crypto.Cipher
 * @see javax.crypto.SecretKey
 * @since September 2nd, 2024
 */
public interface EncryptionStrategy {

    /**
     * Decrypts the given {@code data}.
     *
     * <p>This method accepts a byte array representing the encrypted data and returns
     * a byte array containing the decrypted (plaintext) version of that data. The specific
     * decryption algorithm and key management details are determined by the implementing class.
     *
     * <p>It is essential that the input data is not null and that the implementing
     * class handles any potential decryption errors appropriately, typically by
     * throwing a runtime exception or similar.
     *
     * @param data the encrypted data to be decrypted.
     * @return a byte array containing the decrypted data.
     * @throws RuntimeException if an error occurs during decryption.
     */
    byte[] decrypt(byte[] data);

    /**
     * Encrypts the given byte array of {@code data}.
     *
     * <p>This method takes a byte array as input, representing the data to be encrypted,
     * and returns the encrypted byte array. The exact encryption algorithm and method used
     * are determined by the implementing class.</p>
     *
     * <p>The encryption process typically involves transforming the plaintext data into
     * ciphertext using an encryption algorithm. The output is a byte array containing the
     * encrypted data, which can be stored or transmitted securely.</p>
     *
     * @param data The data to be encrypted, provided as a byte array.
     * @return The encrypted data, also represented as a byte array.
     */
    byte[] encrypt(byte[] data);
}