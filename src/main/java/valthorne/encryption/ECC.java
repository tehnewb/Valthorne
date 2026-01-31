package valthorne.encryption;

import javax.crypto.Cipher;
import java.security.*;

/**
 * The {@code ECC} class provides an implementation of the {@link EncryptionStrategy}
 * interface using the Elliptic Curve Integrated Encryption Scheme (ECIES). ECIES is a hybrid
 * encryption scheme based on elliptic curve cryptography (ECC), offering strong security with
 * smaller key sizes compared to RSA.
 *
 * <p>This class uses a public key for encryption and a private key for decryption. It is suitable
 * for encrypting small data payloads, such as symmetric keys, rather than large datasets.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * KeyPair keyPair = ECC.generateKeyPair();
 * ECC eccEncryption = new ECC(keyPair.getPublic(), keyPair.getPrivate());
 * byte[] plaintext = "Secret Message".getBytes("UTF-8");
 * byte[] encryptedData = eccEncryption.encrypt(plaintext);
 * byte[] decryptedData = eccEncryption.decrypt(encryptedData);
 * System.out.println(new String(decryptedData, "UTF-8")); // Outputs: Secret Message
 * }</pre>
 *
 * <p><strong>Note:</strong> ECIES is not natively supported in JCA's default configuration.
 * This implementation assumes a simplified approach using "ECIES" from a provider like Bouncy Castle.</p>
 *
 * <p>This class is not thread-safe. External synchronization is required for concurrent access.</p>
 *
 * @author Albert Beaupre
 * @since March 13, 2025
 */
public record ECC(PublicKey publicKey, PrivateKey privateKey) implements EncryptionStrategy {

    /**
     * Generates an ECC key pair using the secp256r1 curve.
     *
     * <p>This method generates a public-private key pair suitable for ECIES encryption.
     * The secp256r1 curve provides a good balance of security and performance.</p>
     *
     * @return a {@link KeyPair} containing the generated public and private keys
     * @throws RuntimeException if key pair generation fails
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(256); // 256-bit key size (secp256r1)
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Cannot generate ECC KeyPair", e);
        }
    }

    /**
     * Encrypts the given {@code data} using the ECIES algorithm with the public key.
     *
     * <p>This method initializes a {@link Cipher} in encryption mode with the provided
     * public key and processes the input data to produce an encrypted byte array.</p>
     *
     * @param data the plaintext data to be encrypted
     * @return a byte array containing the encrypted data
     * @throws RuntimeException if an error occurs during encryption
     */
    @Override
    public byte[] encrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("ECIES");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Cannot encrypt using ECIES", e);
        }
    }

    /**
     * Decrypts the given {@code data} using the ECIES algorithm with the private key.
     *
     * <p>This method initializes a {@link Cipher} in decryption mode with the provided
     * private key and processes the encrypted data to produce the decrypted plaintext.</p>
     *
     * @param data the encrypted data to be decrypted
     * @return a byte array containing the decrypted (plaintext) data
     * @throws RuntimeException if an error occurs during decryption
     */
    @Override
    public byte[] decrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("ECIES");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Cannot decrypt using ECIES", e);
        }
    }
}