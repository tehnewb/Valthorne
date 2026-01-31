package valthorne.encryption;

import javax.crypto.Cipher;
import java.security.*;

/**
 * The {@code RSA} class provides an implementation of the {@link EncryptionStrategy}
 * interface using the RSA (Rivest-Shamir-Adleman) encryption algorithm. RSA is a
 * widely-used public-key crypto system that enables secure data transmission through
 * the use of paired public and private keys.
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * KeyPair keyPair = RSA.generateKeyPair(2048);
 * RSA rsaEncryption = new RSA(keyPair.getPublic(), keyPair.getPrivate());
 *
 * byte[] plaintext = "Sensitive Data".getBytes(StandardCharsets.UTF_8);
 * byte[] encryptedData = rsaEncryption.encrypt(plaintext);
 * byte[] decryptedData = rsaEncryption.decrypt(encryptedData);
 *
 * String decryptedText = new String(decryptedData, StandardCharsets.UTF_8);
 * System.out.println(decryptedText);  // Outputs: Sensitive Data
 * }</pre>
 *
 * <p>This class is not thread-safe. If multiple threads access an instance
 * concurrently, and at least one of the threads modifies the instance, it must
 * be synchronized externally.
 *
 * @author Albert Beaupre
 * @since September 2nd, 2024
 */
public record RSA(PublicKey publicKey, PrivateKey privateKey) implements EncryptionStrategy {

    /**
     * Generates an RSA key pair with the specified key size.
     *
     * <p>The generated key pair consists of a public key and a private key. The public key
     * is used for encryption, while the private key is used for decryption. The strength
     * of the RSA encryption depends on the size of the key pair, typically measured in bits.
     *
     * @param keySize the size of the RSA key pair to generate, commonly 1024, 2048, or 4096 bits.
     * @return a {@link KeyPair} containing the generated public and private keys.
     * @throws RuntimeException if key pair generation fails due to a {@link NoSuchAlgorithmException}.
     */
    public static KeyPair generateKeyPair(int keySize) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(keySize);
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Cannot generate RSA KeyPair", e);
        }
    }

    /**
     * Encrypts data using the RSA algorithm with the provided public key.
     *
     * <p>This method initializes a {@link Cipher} in encryption mode with the provided
     * public key and processes the input data to produce an encrypted byte array.
     * The RSA algorithm is suitable for encrypting small amounts of data, such as
     * symmetric keys or hashes, rather than large files.
     *
     * @param data the plaintext data to be encrypted.
     * @return a byte array containing the encrypted data.
     * @throws RuntimeException if an error occurs during encryption, such as an
     *                          {@link InvalidKeyException} or a {@link GeneralSecurityException}.
     */
    @Override
    public byte[] encrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Cannot encrypt using RSA algorithm", e);
        }
    }

    /**
     * Decrypts data using the RSA algorithm with the provided private key.
     *
     * <p>This method initializes a {@link Cipher} in decryption mode with the provided
     * private key and processes the encrypted data to produce a decrypted byte array.
     * The private key must correspond to the public key that was used for encryption.
     *
     * @param data the encrypted data to be decrypted.
     * @return a byte array containing the decrypted (plaintext) data.
     * @throws RuntimeException if an error occurs during decryption, such as an
     *                          {@link InvalidKeyException} or a {@link GeneralSecurityException}.
     */
    @Override
    public byte[] decrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Cannot decrypt using RSA algorithm", e);
        }
    }
}
