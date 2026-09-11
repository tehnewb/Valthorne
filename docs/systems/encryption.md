# Encryption and hashing adapters

Author: Albert Beaupre

[System manual](README.md)

## Purpose

The encryption package adapts several Java cryptography operations to the engine's byte-array interfaces. Symmetric encryption, asymmetric operations, hashing, and ISAAC random generation serve different purposes even when a common interface presents encrypt/decrypt-style names. This guide documents the adapters, not a complete key-management or authentication protocol.

## Features and when to use them

| Feature | Purpose |
| --- | --- |
| Symmetric adapters | AES, AESGCM, Blowfish, and ChaCha20Poly1305 operate with symmetric key material and format-specific parameters. |
| Asymmetric adapters | RSA and ECC use public/private key roles and provider-supported transformations. |
| Hashing | SHA256, SHA512, and Whirlpool produce digests; a digest is not reversible encryption. |
| Pseudorandom generation | ISAAC maintains generator state and is separate from the encryption adapter lifecycle. |

## Getting started

1. Choose the adapter that matches the already defined data format and key type.
2. Read its constructor and payload contract, including nonce/IV storage and provider requirements.
3. Keep key material outside ordinary asset caches and log messages.
4. Treat decryption/authentication failure as a failed operation; do not use partial output as valid data.

## Ownership and lifecycle

Instances can retain mutable cipher or generator state and are not universally thread-safe. The interfaces do not provide key rotation, secure storage, transport negotiation, or a password-storage scheme. A raw hash should not be mistaken for those missing systems.

## Important behavior

- A hashing strategy's decrypt operation is unsupported rather than a way to recover input.
- Provider availability can affect transformations such as ECIES.
- Use exact key, nonce, and encoded-layout requirements from the selected adapter; switching classes does not preserve ciphertext compatibility.

## Components and examples

The sections below explain each component and its declared public or protected operations. Expand an operation reference for signatures, parameters, results, and edge cases. Inherited operations are documented with their base type. Examples embedded in component descriptions are integration fragments: supply the surrounding resources and application variables they name.

- [`AES`](#type-aes)
- [`AESGCM`](#type-aesgcm)
- [`Blowfish`](#type-blowfish)
- [`ChaCha20Poly1305`](#type-chacha20poly1305)
- [`ECC`](#type-ecc)
- [`EncryptionStrategy`](#type-encryptionstrategy)
- [`ISAAC`](#type-isaac)
- [`RSA`](#type-rsa)
- [`SHA256`](#type-sha256)
- [`SHA512`](#type-sha512)
- [`Whirlpool`](#type-whirlpool)

<a id="type-aes"></a>

### AES

[Source](../../src/main/java/valthorne/encryption/AES.java#L33)

The `AES` class provides an implementation of the `EncryptionStrategy`
interface using the AES (Advanced Encryption Standard) algorithm.
AES is a symmetric key encryption technique that is widely used for securing
sensitive data. This class uses the Java Cryptography Architecture (JCA) to perform
encryption and decryption operations.

**Usage Example:**

```java
SecretKey secretKey = ... // Obtain or generate a secret key
AES aesEncryption = new AES(secretKey);

byte[] plaintext = "Hello, World!".getBytes(StandardCharsets.UTF_8);
byte[] encryptedData = aesEncryption.encrypt(plaintext);
byte[] decryptedData = aesEncryption.decrypt(encryptedData);

String decryptedText = new String(decryptedData, StandardCharsets.UTF_8);
System.out.println(decryptedText);  // Outputs: Hello, World!
```

This class is not thread-safe. If multiple threads access an instance
concurrently, and at least one of the threads modifies the instance, it must
be synchronized externally.

<details>
<summary>AES operation reference (2 declarations)</summary>

#### encrypt

```java
@Override
    public byte[] encrypt(byte[] data)
```

Encrypts the given data using the AES algorithm.

This method uses the provided `SecretKey` to initialize a
`Cipher` in encryption mode and then processes the input data
to produce an encrypted byte array.

- **`data`** — the plaintext data to be encrypted

**Returns:** a byte array containing the encrypted data

**Throws `RuntimeException`:** if an error occurs during encryption

#### decrypt

```java
@Override
    public byte[] decrypt(byte[] data)
```

Decrypts the given data using the AES algorithm.

This method uses the provided `SecretKey` to initialize a
`Cipher` in decryption mode and then processes the input data
to produce a decrypted byte array.

- **`data`** — the encrypted data to be decrypted

**Returns:** a byte array containing the decrypted (plaintext) data

**Throws `RuntimeException`:** if an error occurs during decryption

</details>

<a id="type-aesgcm"></a>

### AESGCM

[Source](../../src/main/java/valthorne/encryption/AESGCM.java#L31)

The `AESGCM` class provides an implementation of the `EncryptionStrategy`
interface using the AES algorithm in Galois/Counter Mode (GCM). AES-GCM is an authenticated
encryption mode that provides both confidentiality and data integrity through an authentication tag.

This implementation requires a secret key and a 12-byte initialization vector (IV).
The IV must be unique for each encryption operation with the same key to ensure security.

**Usage Example:**

```java
SecretKey secretKey = // Generate or obtain a key
byte[] iv = // Generate a random 12-byte IV
EncryptionStrategy aesGcm = new AESGCM(secretKey, iv);
byte[] plaintext = "Confidential Data".getBytes("UTF-8");
byte[] encryptedData = aesGcm.encrypt(plaintext);
byte[] decryptedData = aesGcm.decrypt(encryptedData);
System.out.println(new String(decryptedData, "UTF-8")); // Outputs: Confidential Data
```

This class is not thread-safe. External synchronization is required for concurrent access.

<details>
<summary>AESGCM operation reference (2 declarations)</summary>

#### encrypt

```java
@Override
    public byte[] encrypt(byte[] data)
```

Encrypts the given `data` using the AES-GCM algorithm.

This method initializes a `Cipher` in encryption mode with the provided
secret key and IV, then processes the input data to produce an encrypted byte array
that includes an authentication tag.

- **`data`** — the plaintext data to be encrypted

**Returns:** a byte array containing the encrypted data and authentication tag

**Throws `RuntimeException`:** if an error occurs during encryption

#### decrypt

```java
@Override
    public byte[] decrypt(byte[] data)
```

Decrypts the given `data` using the AES-GCM algorithm.

This method initializes a `Cipher` in decryption mode with the provided
secret key and IV, then processes the encrypted data to produce the decrypted plaintext.
The authentication tag is verified during decryption.

- **`data`** — the encrypted data (including the authentication tag) to be decrypted

**Returns:** a byte array containing the decrypted (plaintext) data

**Throws `RuntimeException`:** if an error occurs during decryption or if the tag is invalid

</details>

<a id="type-blowfish"></a>

### Blowfish

[Source](../../src/main/java/valthorne/encryption/Blowfish.java#L26)

The `Blowfish` class provides an implementation of the `EncryptionStrategy`
interface using the Blowfish symmetric-key block cipher, designed by Bruce Schneier.
Blowfish is known for its speed and flexibility, supporting key sizes from 32 to 448 bits.

**Usage Example:**

```java
SecretKey secretKey = // Generate or obtain a Blowfish key
EncryptionStrategy blowfish = new Blowfish(secretKey);
byte[] plaintext = "Sensitive Info".getBytes("UTF-8");
byte[] encryptedData = blowfish.encrypt(plaintext);
byte[] decryptedData = blowfish.decrypt(encryptedData);
System.out.println(new String(decryptedData, "UTF-8")); // Outputs: Sensitive Info
```

This class is not thread-safe. External synchronization is required for concurrent access.

<details>
<summary>Blowfish operation reference (2 declarations)</summary>

#### encrypt

```java
@Override
    public byte[] encrypt(byte[] data)
```

Encrypts the given `data` using the Blowfish algorithm.

This method initializes a `Cipher` in encryption mode with the provided
secret key and processes the input data to produce an encrypted byte array.

- **`data`** — the plaintext data to be encrypted

**Returns:** a byte array containing the encrypted data

**Throws `RuntimeException`:** if an error occurs during encryption

#### decrypt

```java
@Override
    public byte[] decrypt(byte[] data)
```

Decrypts the given `data` using the Blowfish algorithm.

This method initializes a `Cipher` in decryption mode with the provided
secret key and processes the encrypted data to produce the decrypted plaintext.

- **`data`** — the encrypted data to be decrypted

**Returns:** a byte array containing the decrypted (plaintext) data

**Throws `RuntimeException`:** if an error occurs during decryption

</details>

<a id="type-chacha20poly1305"></a>

### ChaCha20Poly1305

[Source](../../src/main/java/valthorne/encryption/ChaCha20Poly1305.java#L36)

The `ChaCha20Poly1305` class provides an implementation of the `EncryptionStrategy`
interface using the ChaCha20-Poly1305 algorithm. ChaCha20 is a high-performance stream cipher
designed by Daniel J. Bernstein, and when paired with Poly1305, it provides authenticated
encryption with associated data (AEAD), ensuring both confidentiality and integrity.

This implementation requires a 256-bit secret key and a 96-bit nonce for secure operation.
The nonce must be unique for each encryption operation with the same key to prevent reuse attacks.

**Usage Example:**

```java
SecretKey secretKey = // Generate or obtain a 256-bit key
byte[] nonce = // Generate a random 96-bit nonce
EncryptionStrategy chacha20 = new ChaCha20Poly1305(secretKey, nonce);
byte[] plaintext = "Secure Data".getBytes("UTF-8");
byte[] encryptedData = chacha20.encrypt(plaintext);
byte[] decryptedData = chacha20.decrypt(encryptedData);
System.out.println(new String(decryptedData, "UTF-8")); // Outputs: Secure Data
```

**Note:** This implementation requires Java 11+ with the JCE provider supporting
ChaCha20-Poly1305 (e.g., Bouncy Castle if not natively available).

This class is not thread-safe. If multiple threads access an instance concurrently,
external synchronization is required.

<details>
<summary>ChaCha20Poly1305 operation reference (2 declarations)</summary>

#### encrypt

```java
@Override
    public byte[] encrypt(byte[] data)
```

Encrypts the given `data` using the ChaCha20-Poly1305 algorithm.

This method initializes a `Cipher` in encryption mode with the provided
secret key and nonce, then processes the input data to produce an encrypted byte array
that includes an authentication tag.

- **`data`** — the plaintext data to be encrypted

**Returns:** a byte array containing the encrypted data and authentication tag

**Throws `RuntimeException`:** if an error occurs during encryption, such as an invalid key or nonce

#### decrypt

```java
@Override
    public byte[] decrypt(byte[] data)
```

Decrypts the given `data` using the ChaCha20-Poly1305 algorithm.

This method initializes a `Cipher` in decryption mode with the provided
secret key and nonce, then processes the encrypted data to produce the decrypted
plaintext. The authentication tag is verified during decryption, and an exception
is thrown if the data has been tampered with.

- **`data`** — the encrypted data (including the authentication tag) to be decrypted

**Returns:** a byte array containing the decrypted (plaintext) data

**Throws `RuntimeException`:** if an error occurs during decryption or if the authentication tag is invalid

</details>

<a id="type-ecc"></a>

### ECC

[Source](../../src/main/java/valthorne/encryption/ECC.java#L33)

The `ECC` class provides an implementation of the `EncryptionStrategy`
interface using the Elliptic Curve Integrated Encryption Scheme (ECIES). ECIES is a hybrid
encryption scheme based on elliptic curve cryptography (ECC), offering strong security with
smaller key sizes compared to RSA.

This class uses a public key for encryption and a private key for decryption. It is suitable
for encrypting small data payloads, such as symmetric keys, rather than large datasets.

**Usage Example:**

```java
KeyPair keyPair = ECC.generateKeyPair();
ECC eccEncryption = new ECC(keyPair.getPublic(), keyPair.getPrivate());
byte[] plaintext = "Secret Message".getBytes("UTF-8");
byte[] encryptedData = eccEncryption.encrypt(plaintext);
byte[] decryptedData = eccEncryption.decrypt(encryptedData);
System.out.println(new String(decryptedData, "UTF-8")); // Outputs: Secret Message
```

**Note:** ECIES is not natively supported in JCA's default configuration.
This implementation assumes a simplified approach using "ECIES" from a provider like Bouncy Castle.

This class is not thread-safe. External synchronization is required for concurrent access.

<details>
<summary>ECC operation reference (3 declarations)</summary>

#### generateKeyPair

```java
public static KeyPair generateKeyPair()
```

Generates an ECC key pair using the secp256r1 curve.

This method generates a public-private key pair suitable for ECIES encryption.
The secp256r1 curve provides a good balance of security and performance.

**Returns:** a `KeyPair` containing the generated public and private keys

**Throws `RuntimeException`:** if key pair generation fails

#### encrypt

```java
@Override
    public byte[] encrypt(byte[] data)
```

Encrypts the given `data` using the ECIES algorithm with the public key.

This method initializes a `Cipher` in encryption mode with the provided
public key and processes the input data to produce an encrypted byte array.

- **`data`** — the plaintext data to be encrypted

**Returns:** a byte array containing the encrypted data

**Throws `RuntimeException`:** if an error occurs during encryption

#### decrypt

```java
@Override
    public byte[] decrypt(byte[] data)
```

Decrypts the given `data` using the ECIES algorithm with the private key.

This method initializes a `Cipher` in decryption mode with the provided
private key and processes the encrypted data to produce the decrypted plaintext.

- **`data`** — the encrypted data to be decrypted

**Returns:** a byte array containing the decrypted (plaintext) data

**Throws `RuntimeException`:** if an error occurs during decryption

</details>

<a id="type-encryptionstrategy"></a>

### EncryptionStrategy

[Source](../../src/main/java/valthorne/encryption/EncryptionStrategy.java#L61)

The `EncryptionStrategy` interface defines a contract for implementing
both asymmetric and symmetric encryption and decryption strategies. Classes
that implement this interface are expected to provide mechanisms to securely
encrypt and decrypt data using the appropriate cryptographic algorithms.

The interface is designed to be flexible and can be implemented by classes
that handle different encryption techniques, such as AES for symmetric key
encryption or RSA for asymmetric key encryption. The interface defines two
essential methods: `encrypt(byte[])` for encryption and `decrypt(byte[])`
for decryption.

This interface does not define the specific algorithm or key management
processes, leaving those details to the implementing classes. It is recommended
that implementations provide robust error handling and security features, such as
preventing unauthorized access to the keys used for encryption and decryption.

**Usage Example:**

```java
EncryptionStrategy encryptionStrategy = new AES(secretKey);
byte[] encryptedData = encryptionStrategy.encrypt(plainTextData);
byte[] decryptedData = encryptionStrategy.decrypt(encryptedData);
```

The above example shows how an implementation of the `EncryptionStrategy`
interface can be used to encrypt and decrypt data. In this case, the `AES`
class, which implements the interface, is instantiated and used for symmetric encryption.

**Thread Safety:**

Implementations of this interface are not inherently thread-safe. It is the
responsibility of the implementing class to ensure thread safety if instances
are to be used concurrently across multiple threads.

**Implementing Classes:**

Classes that implement this interface must ensure that they can handle
encryption and decryption operations in a secure manner. Example implementations
include:

- `AES` - An implementation that uses the AES (Advanced Encryption Standard) algorithm.

- `RSA` - An implementation that uses the RSA (Rivest-Shamir-Adleman) algorithm.

- `ChaCha20Poly1305` - An implementation that uses the ChaCha20 stream cipher with Poly1305 authentication.

- `ECC` - An implementation that uses Elliptic Curve Integrated Encryption Scheme (ECIES).

- `AESGCM` - An implementation that uses AES in Galois/Counter Mode for authenticated encryption.

- `Blowfish` - An implementation that uses the Blowfish symmetric-key block cipher.

- `SHA256` - An implementation that uses the SHA-256 hash function (one-way).

- `SHA512` - An implementation that uses the SHA-512 hash function (one-way).

<details>
<summary>EncryptionStrategy operation reference (10 declarations)</summary>

#### SHA512

```java
 SHA512 SHA512
```

A static instance of `SHA512` used to represent a hashing strategy
that provides data integrity verification or secure fingerprinting through
the SHA-512 hashing algorithm.

This instance is a field within the `EncryptionStrategy` class, implementing
a specific strategy for producing a 512-bit hash of input data. The underlying
algorithm is non-reversible, meaning that the method `SHA512#decrypt(byte[])`
is unsupported and will throw an exception if invoked.

SHA-512 is typically employed in cryptographic applications such as digital
signatures, secure password storage, and checksum generation. This instance
can be utilized to ensure the consistency and security of transmitted or
stored data.

#### SHA256

```java
 SHA256 SHA256
```

Represents an instance of `SHA256`, which is an implementation
of the `EncryptionStrategy` interface designed for hash generation
using the SHA-256 algorithm.

This variable provides a mechanism for creating hashed representations
of input data via a secure one-way hashing function. Data encrypted
with SHA-256 cannot be decrypted, due to the nature of hashing algorithms.

The `SHA256` strategy is ideal for data integrity verification and
other scenarios where irreversible data transformation is required.
For encryption and decryption purposes, consider using other encryption
algorithms such as AES or RSA.

When using `SHA256`, attempt to call decryption or any unsupported
operation may result in an `UnsupportedOperationException`.

Immutable and thread-safe implementation ensures safe access in concurrent environments.

#### ChaCha20Poly1305

```java
static ChaCha20Poly1305 ChaCha20Poly1305(SecretKey key, byte[] nonce)
```

Creates a new instance of the `ChaCha20Poly1305` encryption strategy using the specified
secret key and nonce.

This method is a factory method for constructing a `ChaCha20Poly1305` object,
which implements the encryption strategy using the ChaCha20 algorithm with Poly1305
authentication. The provided `SecretKey` is used for encryption and decryption
operations, and the nonce ensures uniqueness for each encryption operation with the
same key.

- **`key`** — the secret key used for ChaCha20-Poly1305 encryption and decryption
- **`nonce`** — the nonce, a byte array used to ensure uniqueness for each encryption operation with the same key; must typically be 12 bytes in length

**Returns:** a new instance of `ChaCha20Poly1305` configured with the specified secret key and nonce

**Throws `IllegalArgumentException`:** if the provided nonce is null or has an invalid length

#### AESGCM

```java
static AESGCM AESGCM(SecretKey key, byte[] iv)
```

Creates a new instance of the `AESGCM` encryption strategy using the specified
secret key and initialization vector (IV).

This method is a factory method for constructing an `AESGCM` object, which
implements the `EncryptionStrategy` interface using the AES algorithm
in Galois/Counter Mode (GCM). The provided `SecretKey` is used for encryption
and decryption operations, and the IV ensures uniqueness for each encryption operation.

- **`key`** — the secret key used for AES-GCM encryption and decryption
- **`iv`** — the initialization vector (IV), must be a 12-byte array used to ensure uniqueness for each encryption operation with the same key

**Returns:** a new instance of `AESGCM` configured with the specified secret key and IV

**Throws `IllegalArgumentException`:** if the IV is invalid or not 12 bytes in length

#### AES

```java
static AES AES(SecretKey key)
```

Creates a new `AES` instance using the specified secret key.

This method is a factory method for constructing an `AES` object,
which implements the `EncryptionStrategy` using the AES (Advanced Encryption Standard)
symmetric-key encryption algorithm. The provided `SecretKey` is used for both
encryption and decryption operations.

- **`key`** — the secret key used for AES encryption and decryption

**Returns:** a new instance of `AES` configured with the specified secret key

#### BLOWFISH

```java
static Blowfish BLOWFISH(SecretKey key)
```

Creates a new `Blowfish` instance using the specified secret key.

This method is a factory method for constructing a `Blowfish` object, which implements
the `EncryptionStrategy` interface using the Blowfish symmetric-key encryption algorithm.
The provided `SecretKey` is used for both encryption and decryption operations.

- **`key`** — the secret key used for Blowfish encryption and decryption

**Returns:** a new instance of `Blowfish` configured with the specified secret key

#### ECC

```java
static ECC ECC(PublicKey publicKey, PrivateKey privateKey)
```

Creates a new `ECC` instance using the specified public and private keys.

This method is a factory method for constructing an `ECC` object, which implements
the `EncryptionStrategy` using the Elliptic Curve Cryptography (ECC) algorithm.
The provided `PublicKey` will be used for encryption, and the `PrivateKey`
will be used for decryption.

- **`publicKey`** — the public key used for encryption
- **`privateKey`** — the private key used for decryption

**Returns:** a new instance of `ECC` configured with the specified keys

#### RSA

```java
static RSA RSA(PublicKey publicKey, PrivateKey privateKey)
```

Creates a new `RSA` instance using the specified public and private keys.

This method is a factory method for constructing an `RSA` object, which
implements the `EncryptionStrategy` using the RSA algorithm. The provided
`PublicKey` will be used for encryption, and the `PrivateKey` will be
used for decryption.

- **`publicKey`** — the public key used for encryption
- **`privateKey`** — the private key used for decryption

**Returns:** a new instance of `RSA` configured with the specified keys

#### decrypt

```java
byte[] decrypt(byte[] data)
```

Decrypts the given `data`.

This method accepts a byte array representing the encrypted data and returns
a byte array containing the decrypted (plaintext) version of that data. The specific
decryption algorithm and key management details are determined by the implementing class.

It is essential that the input data is not null and that the implementing
class handles any potential decryption errors appropriately, typically by
throwing a runtime exception or similar.

- **`data`** — the encrypted data to be decrypted.

**Returns:** a byte array containing the decrypted data.

**Throws `RuntimeException`:** if an error occurs during decryption.

#### encrypt

```java
byte[] encrypt(byte[] data)
```

Encrypts the given byte array of `data`.

This method takes a byte array as input, representing the data to be encrypted,
and returns the encrypted byte array. The exact encryption algorithm and method used
are determined by the implementing class.

The encryption process typically involves transforming the plaintext data into
ciphertext using an encryption algorithm. The output is a byte array containing the
encrypted data, which can be stored or transmitted securely.

- **`data`** — The data to be encrypted, provided as a byte array.

**Returns:** The encrypted data, also represented as a byte array.

</details>

<a id="type-isaac"></a>

### ISAAC

[Source](../../src/main/java/valthorne/encryption/ISAAC.java#L51)

ISAAC (Indirection, Shift, Accumulate, Add, and Count) pseudo-random number generator.

##### Example

```java
// Unseeded (still generates output, but deterministic based on default state).
ISAAC rng = new ISAAC();
int a = rng.nextValue();
int b = rng.nextValue();

// Seeded (recommended): the seed is copied into the internal results buffer before init.
int[] seed = { 1, 2, 3, 4, 5, 6, 7, 8 };
ISAAC seeded = new ISAAC(seed);
int r0 = seeded.nextValue();
int r1 = seeded.nextValue();

// Re-key / re-seed: create a new instance with a different seed.
// (This implementation does not expose a "setSeed" method.)
```

##### What this class does

This class implements Bob Jenkins' ISAAC algorithm. ISAAC is a fast PRNG that produces
32-bit values from a 256-word internal state. It generates results in batches of 256
integers into `results`. Calls to `nextValue()` consume this batch until
depleted, then `generateIsaacResults()` is invoked to produce the next batch.

##### State layout

- `memory` holds the 256-word internal state table.

- `results` holds the 256-word output batch provided to the caller.

- `accumulator`, `lastResult`, and `counter` are the running mixers.

- `count` tracks the remaining unread values in `results`.

##### Seeding behavior

When constructed with a seed, the seed values are copied into `results` and then
`init(boolean)` is run with `true`, causing the seed to be mixed into the
internal state with an additional "second pass". If constructed with no seed, `init(boolean)`
is run with `false`, producing a deterministic sequence from the default state.

<details>
<summary>ISAAC operation reference (5 declarations)</summary>

#### Constructor

```java
public ISAAC()
```

Constructs an ISAAC generator with the default (unseeded) state.

This creates `memory` and `results`, then runs `init(boolean)` with `false`.
The produced sequence is deterministic for this implementation because the initial arrays are zeroed.

#### Constructor

```java
public ISAAC(int[] seed)
```

Constructs an ISAAC generator seeded with the provided int array.

The seed is copied into `results` starting at index 0, then `init(boolean)` is run
with `true`. When the flag is true, seeding affects the state in a more thorough way by
performing a second mixing pass.

This constructor assumes `seed.length <= 256`. If it is larger, the copy would overflow
the `results` array.

- **`seed`** — seed words to mix into the generator state

#### generateIsaacResults

```java
public final void generateIsaacResults()
```

Generates the next batch of 256 ISAAC results into `results`.

This is the core batch generator. It advances `counter`, mixes `accumulator`,
and uses indirection into `memory` via `MASK` to produce the output stream.

The algorithm runs in two half-size loops to match the reference layout, where `j`
starts at `SIZE/2` for the first half and then wraps for the second half.

#### init

```java
public final void init(boolean flag)
```

Initializes or re-initializes the generator state.

This performs the ISAAC initialization routine:

- sets eight mixing variables (a..h) to the golden ratio constant

- scrambles them for 4 rounds to diffuse the initial constant state

- fills `memory` in 8-word chunks, optionally adding `results` as seed input

- if `flag` is true, performs a second pass so the entire seed affects the entire state

- generates the first output batch via `generateIsaacResults()`

- resets `count` so `nextValue()` can consume outputs

- **`flag`** — if true, mixes the seed in two passes; if false, uses only the first pass

#### nextValue

```java
public final int nextValue()
```

Returns the next 32-bit pseudo-random value from the generator.

Values are returned from the pre-generated `results` batch. When the batch is exhausted,
this method regenerates a new batch by calling `generateIsaacResults()`.

This implementation consumes results from the end of the array backward via `count`.

**Returns:** next pseudo-random 32-bit value

</details>

<a id="type-rsa"></a>

### RSA

[Source](../../src/main/java/valthorne/encryption/RSA.java#L32)

The `RSA` class provides an implementation of the `EncryptionStrategy`
interface using the RSA (Rivest-Shamir-Adleman) encryption algorithm. RSA is a
widely-used public-key crypto system that enables secure data transmission through
the use of paired public and private keys.

**Usage Example:**

```java
KeyPair keyPair = RSA.generateKeyPair(2048);
RSA rsaEncryption = new RSA(keyPair.getPublic(), keyPair.getPrivate());

byte[] plaintext = "Sensitive Data".getBytes(StandardCharsets.UTF_8);
byte[] encryptedData = rsaEncryption.encrypt(plaintext);
byte[] decryptedData = rsaEncryption.decrypt(encryptedData);

String decryptedText = new String(decryptedData, StandardCharsets.UTF_8);
System.out.println(decryptedText);  // Outputs: Sensitive Data
```

This class is not thread-safe. If multiple threads access an instance
concurrently, and at least one of the threads modifies the instance, it must
be synchronized externally.

<details>
<summary>RSA operation reference (3 declarations)</summary>

#### generateKeyPair

```java
public static KeyPair generateKeyPair(int keySize)
```

Generates an RSA key pair with the specified key size.

The generated key pair consists of a public key and a private key. The public key
is used for encryption, while the private key is used for decryption. The strength
of the RSA encryption depends on the size of the key pair, typically measured in bits.

- **`keySize`** — the size of the RSA key pair to generate, commonly 1024, 2048, or 4096 bits.

**Returns:** a `KeyPair` containing the generated public and private keys.

**Throws `RuntimeException`:** if key pair generation fails due to a `NoSuchAlgorithmException`.

#### encrypt

```java
@Override
    public byte[] encrypt(byte[] data)
```

Encrypts data using the RSA algorithm with the provided public key.

This method initializes a `Cipher` in encryption mode with the provided
public key and processes the input data to produce an encrypted byte array.
The RSA algorithm is suitable for encrypting small amounts of data, such as
symmetric keys or hashes, rather than large files.

- **`data`** — the plaintext data to be encrypted.

**Returns:** a byte array containing the encrypted data.

**Throws `RuntimeException`:** if an error occurs during encryption, such as an
`InvalidKeyException` or a `GeneralSecurityException`.

#### decrypt

```java
@Override
    public byte[] decrypt(byte[] data)
```

Decrypts data using the RSA algorithm with the provided private key.

This method initializes a `Cipher` in decryption mode with the provided
private key and processes the encrypted data to produce a decrypted byte array.
The private key must correspond to the public key that was used for encryption.

- **`data`** — the encrypted data to be decrypted.

**Returns:** a byte array containing the decrypted (plaintext) data.

**Throws `RuntimeException`:** if an error occurs during decryption, such as an
`InvalidKeyException` or a `GeneralSecurityException`.

</details>

<a id="type-sha256"></a>

### SHA256

[Source](../../src/main/java/valthorne/encryption/SHA256.java#L28)

The `SHA256Strategy` class implements the `EncryptionStrategy` interface
to provide a mechanism for hashing data using the SHA-256 algorithm.

As SHA-256 is a one-way hash function, the `decrypt(byte[])` method is
not supported and will throw an `UnsupportedOperationException` if called.

This class is intended for use cases where data integrity verification is required
rather than data encryption and decryption.

**Usage Example:**

```java
EncryptionStrategy sha256Strategy = new SHA256Strategy();
byte[] hashedData = sha256Strategy.encrypt(plainTextData);
```

The above example shows how to use the `SHA256Strategy` to hash data.
Since hashing is a one-way operation, the `decrypt` method is not applicable.

<details>
<summary>SHA256 operation reference (2 declarations)</summary>

#### encrypt

```java
@Override
    public byte[] encrypt(byte[] data)
```

Hashes the given `data` using the SHA-256 algorithm.

This method converts the input byte array into a SHA-256 hash. The resulting
byte array represents the hash of the input data.

- **`data`** — The data to be hashed, provided as a byte array.

**Returns:** The SHA-256 hash of the input data as a byte array.

#### decrypt

```java
@Override
    public byte[] decrypt(byte[] data)
```

The `decrypt` operation is not supported for SHA-256 as it is a one-way hash function.

- **`data`** — The encrypted data to be decrypted.

**Returns:** This method does not return anything as it is unsupported.

**Throws `UnsupportedOperationException`:** Always thrown as SHA-256 is a one-way hash function.

</details>

<a id="type-sha512"></a>

### SHA512

[Source](../../src/main/java/valthorne/encryption/SHA512.java#L28)

The `SHA512` class implements the `EncryptionStrategy` interface
to provide a mechanism for hashing data using the SHA-512 algorithm.

As SHA-512 is a one-way hash function, the `decrypt(byte[])` method is
not supported and will throw an `UnsupportedOperationException` if called.

This class is intended for use cases where data integrity verification or
secure fingerprinting is required rather than reversible encryption.

**Usage Example:**

```java
EncryptionStrategy sha512Strategy = new SHA512();
byte[] hashedData = sha512Strategy.encrypt(plainTextData);
```

The above example shows how to use the `SHA512` class to hash data.
Since hashing is a one-way operation, the `decrypt` method is not applicable.

<details>
<summary>SHA512 operation reference (2 declarations)</summary>

#### encrypt

```java
@Override
    public byte[] encrypt(byte[] data)
```

Hashes the given `data` using the SHA-512 algorithm.

This method converts the input byte array into a SHA-512 hash. The resulting
byte array represents the hash of the input data, which is 64 bytes long.

- **`data`** — the data to be hashed, provided as a byte array

**Returns:** the SHA-512 hash of the input data as a byte array

**Throws `RuntimeException`:** if the SHA-512 algorithm is not available

#### decrypt

```java
@Override
    public byte[] decrypt(byte[] data)
```

The `decrypt` operation is not supported for SHA-512 as it is a one-way hash function.

- **`data`** — the encrypted data to be decrypted

**Returns:** this method does not return anything as it is unsupported

**Throws `UnsupportedOperationException`:** always thrown as SHA-512 is a one-way hash function

</details>

<a id="type-whirlpool"></a>

### Whirlpool

[Source](../../src/main/java/valthorne/encryption/Whirlpool.java#L68)

Whirlpool hash function implementation (NESSIE reference style API).

##### Example

```java
// One-shot hashing (byte[] slice).
byte[] digest = Whirlpool.whirlpool(data, 0, data.length);

// Streaming-style hashing (NESSIE API).
Whirlpool w = new Whirlpool();
w.NESSIEinit();
w.NESSIEadd("hello world");          // ASCII helper
w.NESSIEadd(moreBytes, moreBits);    // bit-precise update
byte[] out = new byte[64];           // Whirlpool digest is 512 bits
w.NESSIEfinalize(out);
```

##### How this implementation works

Whirlpool processes input in 512-bit (64-byte) blocks and produces a 512-bit (64-byte) digest.
This implementation follows the common NESSIE reference API pattern:

- `NESSIEinit()` resets all internal state

- `NESSIEadd(byte[], long)` feeds bits into the internal buffer

- `NESSIEfinalize(byte[])` pads, appends bit-length, and emits the digest

##### Bit-precise updates

`NESSIEadd(byte[], long)` accepts a bit count (not a byte count). This allows hashing inputs
that are not byte-aligned. Internally, the class maintains:

- a 64-byte buffer

- a counter of how many bits are currently in the buffer

- a 256-bit total length counter stored as a 32-byte big-endian array

##### Transform

The core compression step is performed by `processBuffer()`:

- maps the 64-byte buffer into eight 64-bit words

- runs a 10-round internal block cipher-like permutation

- applies the Miyaguchi\u2013Preneel compression function to update `hash`

**References**

The Whirlpool algorithm was developed by Paulo S. L. M. Barreto and Vincent Rijmen.
This code originates from the reference style implementation (v3.0 2003.03.12) and retains
its structure and naming conventions (e.g., "NESSIE" method names).

<details>
<summary>Whirlpool operation reference (17 declarations)</summary>

#### R

```java
protected static final  int R
```

Number of rounds in Whirlpool's dedicated block cipher.

#### bitLength

```java
protected  byte[] bitLength
```

Total number of hashed bits as a 256-bit big-endian counter.

#### buffer

```java
protected  byte[] buffer
```

Current 512-bit message block buffer (byte-oriented).

#### bufferBits

```java
protected  int bufferBits
```

Number of valid bits currently in `buffer`.

#### bufferPos

```java
protected  int bufferPos
```

Current byte index in `buffer` where new bits will be written.

#### hash

```java
protected  long[] hash
```

Current 512-bit chaining value (eight 64-bit words).

#### K

```java
protected  long[] K
```

Round key schedule state for the internal cipher.

#### L

```java
protected  long[] L
```

Temporary word array used during key/state mixing.

#### block

```java
protected  long[] block
```

Current 512-bit block mapped from `buffer`.

#### state

```java
protected  long[] state
```

Internal cipher state for the current block transform.

#### Constructor

```java
public Whirlpool()
```

Creates a new Whirlpool instance with uninitialized state.

Call `NESSIEinit()` before feeding data, or use `whirlpool(byte[], int, int)`
for one-shot hashing.

#### whirlpool

```java
public static byte[] whirlpool(byte[] data, int off, int len)
```

Convenience one-shot helper that hashes a byte array slice and returns the 64-byte digest.

If `off > 0`, this copies the slice into a new array before hashing. The digest is always
computed over `len` bytes (i.e., `len * 8` bits).

- **`data`** — source byte array
- **`off`** — starting offset into `data`
- **`len`** — number of bytes to hash

**Returns:** 64-byte Whirlpool digest

#### processBuffer

```java
protected void processBuffer()
```

Processes the current 512-bit `buffer` contents and updates `hash`.

This is the compression step. It maps the byte buffer into eight 64-bit words (`block`),
initializes the internal cipher state (`state`) with `block ^ hash`, then executes
`R` rounds where both the key schedule and state are transformed using the precomputed
circulant tables `C` and round constants `rc`.

After the rounds, it applies the Miyaguchi\u2013Preneel construction:
`hash ^= state ^ block`.

#### NESSIEinit

```java
public void NESSIEinit()
```

Resets the instance to the initial hashing state.

This clears the total bit-length counter, resets the internal buffer and counters, and sets the
chaining value `hash` to its initial (all-zero) value.

Call this before starting a new hash computation with this instance.

#### NESSIEadd

```java
public void NESSIEadd(byte[] source, long sourceBits)
```

Feeds input bits into the hash function.

This method accepts an explicit bit count, allowing non-byte-aligned hashing. It updates the
256-bit `bitLength` counter, then shifts bits from `source` into `buffer`.
When the buffer reaches 512 bits, `processBuffer()` is called and the buffer is reset.

The buffer is treated as a bit stream; `bufferBits` and `bufferPos` track the current
write location. This method maintains the invariant `bufferBits < 512` when it returns.

- **`source`** — input bytes containing the bits to hash
- **`sourceBits`** — number of bits from `source` to consume

#### NESSIEfinalize

```java
public void NESSIEfinalize(byte[] digest)
```

Finalizes hashing, writes the digest, and leaves the instance in a "consumed" state.

This performs Whirlpool padding:

- append a single `1` bit

- append `0` bits until the buffer has room for the 256-bit length field

- append the 256-bit total bit-length stored in `bitLength`

After padding, it processes the final block and writes the 512-bit digest (64 bytes) to `digest`
in big-endian word order.

- **`digest`** — output buffer that receives exactly 64 bytes

#### NESSIEadd

```java
public void NESSIEadd(String source)
```

Convenience helper that hashes an ASCII string using `NESSIEadd(byte[], long)`.

This method converts each `char` to a single byte via simple casting, which matches the
original reference behavior. It is intended for ASCII text only. For UTF-8 or other encodings,
convert the string yourself and call `NESSIEadd(byte[], long)`.

- **`source`** — ASCII plaintext string to hash

</details>

## Related guides

- [Binary buffers and byte order](buffers.md)
- [Classpath and filesystem utilities](files.md)
- [Compression strategies](compression.md)
