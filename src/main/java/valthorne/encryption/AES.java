package valthorne.encryption;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

/**
 * The {@code AES} class provides an implementation of the {@link EncryptionStrategy}
 * interface using the AES (Advanced Encryption Standard) algorithm.
 * AES is a symmetric key encryption technique that is widely used for securing
 * sensitive data. This class uses the Java Cryptography Architecture (JCA) to perform
 * encryption and decryption operations.
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * SecretKey secretKey = ... // Obtain or generate a secret key
 * AES aesEncryption = new AES(secretKey);
 *
 * byte[] plaintext = "Hello, World!".getBytes(StandardCharsets.UTF_8);
 * byte[] encryptedData = aesEncryption.encrypt(plaintext);
 * byte[] decryptedData = aesEncryption.decrypt(encryptedData);
 *
 * String decryptedText = new String(decryptedData, StandardCharsets.UTF_8);
 * System.out.println(decryptedText);  // Outputs: Hello, World!
 * }</pre>
 *
 * <p>This class is not thread-safe. If multiple threads access an instance
 * concurrently, and at least one of the threads modifies the instance, it must
 * be synchronized externally.
 *
 * @author Albert Beaupre
 * @since September 2nd, 2024
 */
public record AES(SecretKey key) implements EncryptionStrategy {

    /**
     * Encrypts the given data using the AES algorithm.
     *
     * <p>This method uses the provided {@link SecretKey} to initialize a
     * {@link Cipher} in encryption mode and then processes the input data
     * to produce an encrypted byte array.
     *
     * @param data the plaintext data to be encrypted
     * @return a byte array containing the encrypted data
     * @throws RuntimeException if an error occurs during encryption
     */
    @Override
    public byte[] encrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Cannot encrypt using AES Algorithm", e);
        }
    }

    /**
     * Decrypts the given data using the AES algorithm.
     *
     * <p>This method uses the provided {@link SecretKey} to initialize a
     * {@link Cipher} in decryption mode and then processes the input data
     * to produce a decrypted byte array.
     *
     * @param data the encrypted data to be decrypted
     * @return a byte array containing the decrypted (plaintext) data
     * @throws RuntimeException if an error occurs during decryption
     */
    @Override
    public byte[] decrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Cannot decrypt using AES Algorithm", e);
        }
    }
}