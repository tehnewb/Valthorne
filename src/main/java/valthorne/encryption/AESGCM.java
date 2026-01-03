package valthorne.encryption;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * The {@code AESGCM} class provides an implementation of the {@link EncryptionStrategy}
 * interface using the AES algorithm in Galois/Counter Mode (GCM). AES-GCM is an authenticated
 * encryption mode that provides both confidentiality and data integrity through an authentication tag.
 *
 * <p>This implementation requires a secret key and a 12-byte initialization vector (IV).
 * The IV must be unique for each encryption operation with the same key to ensure security.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * SecretKey secretKey = // Generate or obtain a key
 * byte[] iv = // Generate a random 12-byte IV
 * EncryptionStrategy aesGcm = new AESGCM(secretKey, iv);
 * byte[] plaintext = "Confidential Data".getBytes("UTF-8");
 * byte[] encryptedData = aesGcm.encrypt(plaintext);
 * byte[] decryptedData = aesGcm.decrypt(encryptedData);
 * System.out.println(new String(decryptedData, "UTF-8")); // Outputs: Confidential Data
 * }</pre>
 *
 * <p>This class is not thread-safe. External synchronization is required for concurrent access.</p>
 *
 * @author Albert Beaupre
 * @since March 13, 2025
 */
public record AESGCM(SecretKey key, byte[] iv) implements EncryptionStrategy {

    /**
     * Encrypts the given {@code data} using the AES-GCM algorithm.
     *
     * <p>This method initializes a {@link Cipher} in encryption mode with the provided
     * secret key and IV, then processes the input data to produce an encrypted byte array
     * that includes an authentication tag.</p>
     *
     * @param data the plaintext data to be encrypted
     * @return a byte array containing the encrypted data and authentication tag
     * @throws RuntimeException if an error occurs during encryption
     */
    @Override
    public byte[] encrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv); // 128-bit tag length
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Cannot encrypt using AES-GCM", e);
        }
    }

    /**
     * Decrypts the given {@code data} using the AES-GCM algorithm.
     *
     * <p>This method initializes a {@link Cipher} in decryption mode with the provided
     * secret key and IV, then processes the encrypted data to produce the decrypted plaintext.
     * The authentication tag is verified during decryption.</p>
     *
     * @param data the encrypted data (including the authentication tag) to be decrypted
     * @return a byte array containing the decrypted (plaintext) data
     * @throws RuntimeException if an error occurs during decryption or if the tag is invalid
     */
    @Override
    public byte[] decrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv); // 128-bit tag length
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Cannot decrypt using AES-GCM", e);
        }
    }
}