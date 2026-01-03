package valthorne.encryption;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.ChaCha20ParameterSpec;

/**
 * The {@code ChaCha20Poly1305} class provides an implementation of the {@link EncryptionStrategy}
 * interface using the ChaCha20-Poly1305 algorithm. ChaCha20 is a high-performance stream cipher
 * designed by Daniel J. Bernstein, and when paired with Poly1305, it provides authenticated
 * encryption with associated data (AEAD), ensuring both confidentiality and integrity.
 *
 * <p>This implementation requires a 256-bit secret key and a 96-bit nonce for secure operation.
 * The nonce must be unique for each encryption operation with the same key to prevent reuse attacks.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * SecretKey secretKey = // Generate or obtain a 256-bit key
 * byte[] nonce = // Generate a random 96-bit nonce
 * EncryptionStrategy chacha20 = new ChaCha20Poly1305(secretKey, nonce);
 * byte[] plaintext = "Secure Data".getBytes("UTF-8");
 * byte[] encryptedData = chacha20.encrypt(plaintext);
 * byte[] decryptedData = chacha20.decrypt(encryptedData);
 * System.out.println(new String(decryptedData, "UTF-8")); // Outputs: Secure Data
 * }</pre>
 *
 * <p><strong>Note:</strong> This implementation requires Java 11+ with the JCE provider supporting
 * ChaCha20-Poly1305 (e.g., Bouncy Castle if not natively available).</p>
 *
 * <p>This class is not thread-safe. If multiple threads access an instance concurrently,
 * external synchronization is required.</p>
 *
 * @author Albert Beaupre
 * @since March 13, 2025
 */
public record ChaCha20Poly1305(SecretKey key, byte[] nonce) implements EncryptionStrategy {

    /**
     * Encrypts the given {@code data} using the ChaCha20-Poly1305 algorithm.
     *
     * <p>This method initializes a {@link Cipher} in encryption mode with the provided
     * secret key and nonce, then processes the input data to produce an encrypted byte array
     * that includes an authentication tag.</p>
     *
     * @param data the plaintext data to be encrypted
     * @return a byte array containing the encrypted data and authentication tag
     * @throws RuntimeException if an error occurs during encryption, such as an invalid key or nonce
     */
    @Override
    public byte[] encrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
            ChaCha20ParameterSpec spec = new ChaCha20ParameterSpec(nonce, 1); // Counter starts at 1
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Cannot encrypt using ChaCha20-Poly1305", e);
        }
    }

    /**
     * Decrypts the given {@code data} using the ChaCha20-Poly1305 algorithm.
     *
     * <p>This method initializes a {@link Cipher} in decryption mode with the provided
     * secret key and nonce, then processes the encrypted data to produce the decrypted
     * plaintext. The authentication tag is verified during decryption, and an exception
     * is thrown if the data has been tampered with.</p>
     *
     * @param data the encrypted data (including the authentication tag) to be decrypted
     * @return a byte array containing the decrypted (plaintext) data
     * @throws RuntimeException if an error occurs during decryption or if the authentication tag is invalid
     */
    @Override
    public byte[] decrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
            ChaCha20ParameterSpec spec = new ChaCha20ParameterSpec(nonce, 1); // Same nonce and counter
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Cannot decrypt using ChaCha20-Poly1305", e);
        }
    }
}