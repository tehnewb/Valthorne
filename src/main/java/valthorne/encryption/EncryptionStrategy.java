package valthorne.encryption;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;

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
     * A static instance of {@code SHA512} used to represent a hashing strategy
     * that provides data integrity verification or secure fingerprinting through
     * the SHA-512 hashing algorithm.
     *
     * <p>This instance is a field within the {@code EncryptionStrategy} class, implementing
     * a specific strategy for producing a 512-bit hash of input data. The underlying
     * algorithm is non-reversible, meaning that the method {@link SHA512#decrypt(byte[])}
     * is unsupported and will throw an exception if invoked.
     *
     * <p>SHA-512 is typically employed in cryptographic applications such as digital
     * signatures, secure password storage, and checksum generation. This instance
     * can be utilized to ensure the consistency and security of transmitted or
     * stored data.
     */
    SHA512 SHA512 = new SHA512();

    /**
     * Represents an instance of {@code SHA256}, which is an implementation
     * of the {@link EncryptionStrategy} interface designed for hash generation
     * using the SHA-256 algorithm.
     *
     * <p>This variable provides a mechanism for creating hashed representations
     * of input data via a secure one-way hashing function. Data encrypted
     * with SHA-256 cannot be decrypted, due to the nature of hashing algorithms.
     *
     * <p>The {@code SHA256} strategy is ideal for data integrity verification and
     * other scenarios where irreversible data transformation is required.
     * For encryption and decryption purposes, consider using other encryption
     * algorithms such as AES or RSA.</p>
     *
     * <p>When using {@code SHA256}, attempt to call decryption or any unsupported
     * operation may result in an {@link UnsupportedOperationException}.</p>
     *
     * <p>Immutable and thread-safe implementation ensures safe access in concurrent environments.</p>
     */
    SHA256 SHA256 = new SHA256();

    /**
     * Creates a new instance of the {@code ChaCha20Poly1305} encryption strategy using the specified
     * secret key and nonce.
     * <p>
     * This method is a factory method for constructing a {@code ChaCha20Poly1305} object,
     * which implements the encryption strategy using the ChaCha20 algorithm with Poly1305
     * authentication. The provided {@link SecretKey} is used for encryption and decryption
     * operations, and the nonce ensures uniqueness for each encryption operation with the
     * same key.
     *
     * @param key   the secret key used for ChaCha20-Poly1305 encryption and decryption
     * @param nonce the nonce, a byte array used to ensure uniqueness for each encryption
     *              operation with the same key; must typically be 12 bytes in length
     * @return a new instance of {@code ChaCha20Poly1305} configured with the specified secret key and nonce
     * @throws IllegalArgumentException if the provided nonce is null or has an invalid length
     */
    static ChaCha20Poly1305 ChaCha20Poly1305(SecretKey key, byte[] nonce) {
        return new ChaCha20Poly1305(key, nonce);
    }

    /**
     * Creates a new instance of the {@code AESGCM} encryption strategy using the specified
     * secret key and initialization vector (IV).
     *
     * <p>This method is a factory method for constructing an {@link AESGCM} object, which
     * implements the {@link EncryptionStrategy} interface using the AES algorithm
     * in Galois/Counter Mode (GCM). The provided {@link SecretKey} is used for encryption
     * and decryption operations, and the IV ensures uniqueness for each encryption operation.
     *
     * @param key the secret key used for AES-GCM encryption and decryption
     * @param iv  the initialization vector (IV), must be a 12-byte array used to ensure uniqueness
     *            for each encryption operation with the same key
     * @return a new instance of {@link AESGCM} configured with the specified secret key and IV
     * @throws IllegalArgumentException if the IV is invalid or not 12 bytes in length
     */
    static AESGCM AESGCM(SecretKey key, byte[] iv) {
        return new AESGCM(key, iv);
    }

    /**
     * Creates a new {@code AES} instance using the specified secret key.
     * <p>
     * This method is a factory method for constructing an {@link AES} object,
     * which implements the {@link EncryptionStrategy} using the AES (Advanced Encryption Standard)
     * symmetric-key encryption algorithm. The provided {@link SecretKey} is used for both
     * encryption and decryption operations.
     *
     * @param key the secret key used for AES encryption and decryption
     * @return a new instance of {@link AES} configured with the specified secret key
     */
    static AES AES(SecretKey key) {
        return new AES(key);
    }

    /**
     * Creates a new {@code Blowfish} instance using the specified secret key.
     * <p>
     * This method is a factory method for constructing a {@link Blowfish} object, which implements
     * the {@link EncryptionStrategy} interface using the Blowfish symmetric-key encryption algorithm.
     * The provided {@link SecretKey} is used for both encryption and decryption operations.
     *
     * @param key the secret key used for Blowfish encryption and decryption
     * @return a new instance of {@link Blowfish} configured with the specified secret key
     */
    static Blowfish BLOWFISH(SecretKey key) {
        return new Blowfish(key);
    }

    /**
     * Creates a new {@code ECC} instance using the specified public and private keys.
     * <p>
     * This method is a factory method for constructing an {@link ECC} object, which implements
     * the {@link EncryptionStrategy} using the Elliptic Curve Cryptography (ECC) algorithm.
     * The provided {@link PublicKey} will be used for encryption, and the {@link PrivateKey}
     * will be used for decryption.
     *
     * @param publicKey  the public key used for encryption
     * @param privateKey the private key used for decryption
     * @return a new instance of {@link ECC} configured with the specified keys
     */
    static ECC ECC(PublicKey publicKey, PrivateKey privateKey) {
        return new ECC(publicKey, privateKey);
    }

    /**
     * Creates a new {@code RSA} instance using the specified public and private keys.
     *
     * <p>This method is a factory method for constructing an {@link RSA} object, which
     * implements the {@link EncryptionStrategy} using the RSA algorithm. The provided
     * {@link PublicKey} will be used for encryption, and the {@link PrivateKey} will be
     * used for decryption.
     *
     * @param publicKey  the public key used for encryption
     * @param privateKey the private key used for decryption
     * @return a new instance of {@link RSA} configured with the specified keys
     */
    static RSA RSA(PublicKey publicKey, PrivateKey privateKey) {
        return new RSA(publicKey, privateKey);
    }

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