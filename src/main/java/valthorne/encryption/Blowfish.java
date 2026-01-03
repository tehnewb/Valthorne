package valthorne.encryption;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

/**
 * The {@code Blowfish} class provides an implementation of the {@link EncryptionStrategy}
 * interface using the Blowfish symmetric-key block cipher, designed by Bruce Schneier.
 * Blowfish is known for its speed and flexibility, supporting key sizes from 32 to 448 bits.
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * SecretKey secretKey = // Generate or obtain a Blowfish key
 * EncryptionStrategy blowfish = new Blowfish(secretKey);
 * byte[] plaintext = "Sensitive Info".getBytes("UTF-8");
 * byte[] encryptedData = blowfish.encrypt(plaintext);
 * byte[] decryptedData = blowfish.decrypt(encryptedData);
 * System.out.println(new String(decryptedData, "UTF-8")); // Outputs: Sensitive Info
 * }</pre>
 *
 * <p>This class is not thread-safe. External synchronization is required for concurrent access.</p>
 *
 * @author Albert Beaupre
 * @since March 13, 2025
 */
public record Blowfish(SecretKey key) implements EncryptionStrategy {

    /**
     * Encrypts the given {@code data} using the Blowfish algorithm.
     *
     * <p>This method initializes a {@link Cipher} in encryption mode with the provided
     * secret key and processes the input data to produce an encrypted byte array.</p>
     *
     * @param data the plaintext data to be encrypted
     * @return a byte array containing the encrypted data
     * @throws RuntimeException if an error occurs during encryption
     */
    @Override
    public byte[] encrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("Blowfish");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Cannot encrypt using Blowfish", e);
        }
    }

    /**
     * Decrypts the given {@code data} using the Blowfish algorithm.
     *
     * <p>This method initializes a {@link Cipher} in decryption mode with the provided
     * secret key and processes the encrypted data to produce the decrypted plaintext.</p>
     *
     * @param data the encrypted data to be decrypted
     * @return a byte array containing the decrypted (plaintext) data
     * @throws RuntimeException if an error occurs during decryption
     */
    @Override
    public byte[] decrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("Blowfish");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Cannot decrypt using Blowfish", e);
        }
    }
}