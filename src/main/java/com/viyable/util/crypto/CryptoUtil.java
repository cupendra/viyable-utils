package com.viyable.util.crypto;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;

public class CryptoUtil {
	protected static final String CBC_WITH_PKCS5PADDING = "AES/CBC/PKCS5Padding";
	protected static final String GCM_WITH_NOPADDING = "AES/GCM/NoPadding";
	protected static final int GCM_AES_KEY_SIZE = 256;
	protected static final int GCM_IV_LENGTH = 12;
	protected static final int GCM_TAG_LENGTH = 16;
	
	private static Key getSHA1DigestKey (String saltKey) {
		SecretKeySpec secretKeySpec = null;
		try {
			byte[] key = saltKey.getBytes(StandardCharsets.UTF_8);
			MessageDigest sha = MessageDigest.getInstance("SHA-1");
			key = sha.digest(key);
			key = Arrays.copyOf(key, 16); // use only first 128 bit

			secretKeySpec = new SecretKeySpec(key, "AES");
		}catch(Exception ex) {
			ex.printStackTrace();
		}
		
		
		return secretKeySpec;
	}
	
	public static String encrypt (String textToBeEncryted, String saltKey) {
		String encryptedText = null;
		if (textToBeEncryted != null) {
			try {
				Cipher cipher = Cipher.getInstance(CBC_WITH_PKCS5PADDING);
				Key key = getSHA1DigestKey(saltKey);
				cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(new byte[16]));
				
				byte []result = cipher.doFinal(textToBeEncryted.getBytes(StandardCharsets.UTF_8));
				encryptedText = Base64.getEncoder().encodeToString(result);
			} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                     IllegalBlockSizeException | BadPaddingException |
                     InvalidAlgorithmParameterException e) {
				e.printStackTrace();
			}
        }
		
		
		return encryptedText;
	}
	
	public static String decrypt (String textToBeDecryted, String saltKey) {
		String decryptedText = null;
		if (textToBeDecryted != null) {
			try {
				Cipher cipher = Cipher.getInstance(CBC_WITH_PKCS5PADDING);
				Key key = getSHA1DigestKey(saltKey);
				cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(new byte[16]));
				
				byte []enbytes = Base64.getDecoder().decode(textToBeDecryted);
				
				byte []result = cipher.doFinal(enbytes);
				
				decryptedText = new String(result, StandardCharsets.UTF_8);
			} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                     IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException e) {
				e.printStackTrace();
			}
        }
		
		
		return decryptedText;
	}

	/**
	 * Returns the <code>SecretKeySpec</code> objected created with extracted bytes from the given security key
	 *
	 * @param secretKeyString, the security token to be used for encrypting and decrypting text
	 *
	 * @return SecuritySpec object to be used in Cipher generation
	 *
	 * @throws UnsupportedEncodingException, if the UTF-8 charset cannot be applied on the given security token to extract its bytes
	 *
	 */
	private static SecretKeySpec getSecureSpecKey (String secretKeyString)throws UnsupportedEncodingException {
		SecretKeySpec secretKey = null;

		byte[] key = secretKeyString.getBytes(StandardCharsets.UTF_8);

		secretKey = new SecretKeySpec(key, "AES");

		return secretKey;
	}

	/**
	 * Generates and returns a random secure key of length 256 for AES GCM algorithm
	 *
	 * @return <code>SecretKey</code> object to be used in Cipher generation
	 *
	 * @throws NoSuchAlgorithmException, if the given algorithm is not found
	 */
	public static SecretKey getRandomKeyForGCM()throws NoSuchAlgorithmException {
		SecretKey key = null;
		try {
			KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
			keyGenerator.init(GCM_AES_KEY_SIZE);

			key = keyGenerator.generateKey();
		} catch (NoSuchAlgorithmException e) {
			throw e;
		}

		return key;
	}

	/**
	 * Returns the <code>SecretKeySpec</code> objected created with a message digest hashing algorithms applied on the extracted bytes from the given security key
	 *
	 * @param secretKeyString, the security token to be used for encrypting and decrypting text
	 * @param digestAlgorithm, the message digest hashing algorithm to be applied on the security key string bytes
	 *
	 * @return SecuritySpec object to be used in Cipher generation
	 *
	 * @throws UnsupportedEncodingException, if the UTF-8 charset cannot be applied on the given security token to extract its bytes
	 * @throws NoSuchAlgorithmException, if the given digest algorithm is not found
	 */
	private static SecretKeySpec getDigestedKey (String secretKeyString, String digestAlgorithm) throws UnsupportedEncodingException, NoSuchAlgorithmException{
		SecretKeySpec secretKey = null;

		try {
			byte[] key = secretKeyString.getBytes(StandardCharsets.UTF_8);
			MessageDigest sha = MessageDigest.getInstance(digestAlgorithm);
			key = sha.digest(key);

			secretKey = new SecretKeySpec(key, "AES");
		} catch (NoSuchAlgorithmException e) {
			throw e;
		}

		return secretKey;
	}

	/**
	 * Creates and returns the byte array of the given iv string. The byte array would be used as initialization vector during encryption and decryption
	 *
	 * @param ivKeyString, that would be used to convert into byte array
	 *
	 * @return the byte[] from the given iv string
	 *
	 * @throws UnsupportedEncodingException, if the UTF-8 charset cannot be applied on the given iv key token to extract its bytes
	 */
	private static byte[] getIVKey (String ivKeyString) throws UnsupportedEncodingException {
		return  ivKeyString.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Returns a random IV 12 character byte array for AES-GCM Algorithm
	 *
	 * @return a byte array with specified length
	 */
	public static byte[] getRandomIVKeyForGCM() {
		byte[] iv = new byte[GCM_IV_LENGTH];
		SecureRandom random = new SecureRandom();
		random.nextBytes(iv);

		return iv;
	}

	/**
	 * Encrypts the given string as per the AES CBC PKCS5 padding algorithm by creating the security key spec from the given security key token.
	 * Initialization vector(iv) for CBC algorithm is generated from the given ivKey string bytes
	 *
	 * Returns the encrypted text in BASE64 encoded format.
	 *
	 * @param textToBeEncrypted, the actual text to be encrypted
	 * @param securityKey, the security key that must be used to encrypt the given text
	 * @param ivKey, the key to be used for constructing initialization vector
	 *
	 * @return the encrypted text in the BASE64 encoded format
	 *
	 * @throws EncryptDecryptException, the exception if the encryption algorithm fails due to the cipher block size or padding issue or for any other reason
	 */
	public static String encryptCBCWithPKCS5Padding(String textToBeEncrypted, String securityKey, String ivKey)throws EncryptDecryptException {
		String encryptedText = "";

		try {
			Cipher cipher = Cipher.getInstance(CBC_WITH_PKCS5PADDING);

			byte[] initVector = getIVKey(ivKey);
			IvParameterSpec ivSpec = new IvParameterSpec(initVector);
			SecureRandom secRandom = new SecureRandom() ;
			secRandom.nextBytes(initVector);

			cipher.init(Cipher.ENCRYPT_MODE, getSecureSpecKey(securityKey), ivSpec,secRandom);
			byte[] encoded = textToBeEncrypted.getBytes(StandardCharsets.UTF_8);

			byte[] ciphertext = cipher.doFinal(encoded);

			encryptedText = Base64.getEncoder().encodeToString(ciphertext);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | UnsupportedEncodingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
			throw new EncryptDecryptException(e);
		}



		return encryptedText;
	}

	/**
	 * Decrypts the given base64 encoded text using AES CBC PKCS5 padding algorithm by creating the security key spec from the given security key token.
	 * Initialization vector(iv) for CBC algorithm is generated from the given ivKey string bytes
	 *
	 * Returns the decrypted text in string format.
	 *
	 * @param base64TextToBeDecrypted, the encrypted text in base64 format that must be used for decryption
	 * @param securityKey, the security key that must be used to decrypt the given text. This must be same as the one used for encryption
	 * @param ivKey, the key to be used for constructing initialization vector
	 *
	 * @return the actual text after decryption in string format. If failed to decrypt returns an empty string
	 *
	 * @throws EncryptDecryptException, the exception if the encryption algorithm fails due to the cipher block size or padding issue or for any other reason
	 */
	public static String decryptCBCWithPKCS5Padding(String base64TextToBeDecrypted, String securityKey, String ivKey)throws EncryptDecryptException {
		String decryptedText = "";

		try {
			Cipher cipher = Cipher.getInstance(CBC_WITH_PKCS5PADDING);

			byte []cipherText = Base64.getDecoder().decode(base64TextToBeDecrypted);

			IvParameterSpec ivSpec = new IvParameterSpec(getIVKey(ivKey));
			cipher.init(Cipher.DECRYPT_MODE, getSecureSpecKey(securityKey), ivSpec);

			byte[] plainText = cipher.doFinal(cipherText);
			decryptedText = new String(plainText);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | UnsupportedEncodingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
			throw new EncryptDecryptException(e);
		}

		return decryptedText;
	}

	/**
	 * Encrypts the given string as per the AES CBC PKCS5 padding algorithm by using the <code>SecurityKeySpec</code> object constructed from a security token string.
	 * Initialization vector(iv) for CBC algorithm is generated randomly from the <code>SecureRandom</code> object and the cipher block size
	 *
	 * Returns the encrypted text in BASE64 encoded format.
	 *
	 * @param textToBeEncrypted, the actual text to be encrypted
	 * @param key, the <code>SecurityKeySpec</code> that must be used to decrypt the given text. This must have been created using the same security token used for encryption
	 *
	 * @return the encrypted text in the BASE64 encoded format
	 *
	 * @throws EncryptDecryptException, the exception if the encryption algorithm fails due to the cipher block size or padding issue or for any other reason
	 */
	private static String encryptWithRandomIV (String textToBeEncrypted, SecretKeySpec key)throws EncryptDecryptException {
		String encryptedText = "";

		try {
			Cipher cipher = Cipher.getInstance(CBC_WITH_PKCS5PADDING);

			final int blockSize = cipher.getBlockSize();

			byte[] initVector = new byte[blockSize];
			(new SecureRandom()).nextBytes(initVector);
			IvParameterSpec ivSpec = new IvParameterSpec(initVector);
			cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);

			byte[] encoded = textToBeEncrypted.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			byte[] ciphertext = new byte[initVector.length + cipher.getOutputSize(encoded.length)];
			for (int i=0; i < initVector.length; i++) {
				ciphertext[i] = initVector[i];
			}
			cipher.doFinal(encoded, 0, encoded.length, ciphertext, initVector.length);

			encryptedText = Base64.getEncoder().encodeToString(ciphertext);

		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | ShortBufferException | IllegalBlockSizeException | BadPaddingException e) {
			throw new EncryptDecryptException(e);
		}

		return encryptedText;
	}

	/**
	 * Decrypts the given base64 encoded text using AES CBC PKCS5 padding algorithm by using the <code>SecurityKeySpec</code> object constructed from a security token string.
	 * Initialization vector(iv) for CBC algorithm is extracted after decoding the given base64 encoded encrypted text into its original byte format and the cipher block size
	 *
	 * Returns the decrypted text in string format.
	 *
	 * @param base64TextToBeDecrypted, the encrypted text in base64 format that must be used for decryption
	 * @param key, the <code>SecurityKeySpec</code> that must be used to decrypt the given text. This must have been created using the same security token used for encryption
	 *
	 * @return the actual text after decryption in string format. If failed to decrypt returns an empty string
	 *
	 * @throws EncryptDecryptException, the exception if the encryption algorithm fails due to the cipher block size or padding issue or for any other reason
	 */
	private static String decryptWithRandomIV(String base64TextToBeDecrypted, SecretKeySpec key)throws EncryptDecryptException {
		String decryptedText = "";
		try {
			Cipher cipher = Cipher.getInstance(CBC_WITH_PKCS5PADDING);

			final int blockSize = cipher.getBlockSize();

			byte []cipherText = Base64.getDecoder().decode(base64TextToBeDecrypted);
			byte[] initVector = Arrays.copyOfRange(cipherText, 0, blockSize);
			IvParameterSpec ivSpec = new IvParameterSpec(initVector);

			SecureRandom secRandom = new SecureRandom() ;
			secRandom.nextBytes(initVector);

			cipher.init(Cipher.DECRYPT_MODE, key, ivSpec,secRandom);

			byte[] plainText = cipher.doFinal(cipherText, blockSize, cipherText.length - blockSize);
			decryptedText = new String(plainText);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
			throw new EncryptDecryptException(e);
		}

		return decryptedText;
	}

	/**
	 * Encrypts the given string as per the AES CBC PKCS5 padding algorithm by creating the security key spec from the given security key token.
	 * Initialization vector(iv) for CBC algorithm is generated randomly from the <code>SecureRandom</code> object and the cipher block size
	 *
	 * Returns the encrypted text in BASE64 encoded format.
	 *
	 * @param textToBeEncrypted, the actual text to be encrypted
	 * @param securityKey, the security key that must be used to encrypt the given text
	 *
	 * @return the encrypted text in the BASE64 encoded format
	 *
	 * @throws EncryptDecryptException, the exception if the encryption algorithm fails due to the cipher block size or padding issue or for any other reason
	 */
	public static String encryptCBCWithRandomIVAndPKCS5Padding(String textToBeEncrypted, String securityKey)throws EncryptDecryptException {
		String encryptedText = "";
		try {
			encryptedText = encryptWithRandomIV(textToBeEncrypted, getSecureSpecKey(securityKey));
		} catch (UnsupportedEncodingException | EncryptDecryptException e) {
			throw new EncryptDecryptException(e);
		}

		return encryptedText;
	}

	/**
	 * Decrypts the given base64 encoded text using AES CBC PKCS5 padding algorithm by creating the security key spec from the given security key token.
	 * Initialization vector(iv) for CBC algorithm is extracted after decoding the given base64 encoded encrypted text into its original byte format and the cipher block size
	 *
	 * Returns the decrypted text in string format.
	 *
	 * @param base64TextToBeDecrypted, the encrypted text in base64 format that must be used for decryption
	 * @param securityKey, the security key that must be used to decrypt the given text. This must be same as the one used for encryption
	 *
	 * @return the actual text after decryption in string format. If failed to decrypt returns an empty string
	 *
	 * @throws EncryptDecryptException, the exception if the encryption algorithm fails due to the cipher block size or padding issue or for any other reason
	 */
	public static String decryptCBCWithRandomIVAndPKCS5Padding(String base64TextToBeDecrypted, String securityKey)throws EncryptDecryptException {
		String decryptedText = "";
		try {
			decryptedText = decryptWithRandomIV(base64TextToBeDecrypted, getSecureSpecKey(securityKey));
		} catch (UnsupportedEncodingException | EncryptDecryptException e) {
			throw new EncryptDecryptException(e);
		}

		return decryptedText;
	}

	/**
	 * Encrypts the given string as per the AES CBC PKCS5 padding algorithm.
	 * The security key spec is constructed from the digested hash string derived by applying the given message digest hash algorithm on the given security key token.
	 *
	 * Initialization vector(iv) for CBC algorithm is generated randomly from the <code>SecureRandom</code> object and the cipher block size
	 *
	 * Returns the encrypted text in BASE64 encoded format.
	 *
	 * @param textToBeEncrypted, the actual text to be encrypted
	 * @param securityKey, the security key that must be used to encrypt the given text
	 * @param digestAlgorithm, the message digest hashing algorithm to be applied on the security key string bytes
	 *
	 * @return the encrypted text in the BASE64 encoded format
	 *
	 * @throws EncryptDecryptException, the exception if the encryption algorithm fails due to the cipher block size or padding issue or for any other reason
	 */
	public static String encryptCBCWithRandomIVAndPKCS5Padding(String textToBeEncrypted, String securityKey, String digestAlgorithm)throws EncryptDecryptException {
		String encryptedText = "";
		try {
			encryptedText = encryptWithRandomIV(textToBeEncrypted, getDigestedKey(securityKey, digestAlgorithm));
		} catch (UnsupportedEncodingException | NoSuchAlgorithmException | EncryptDecryptException e) {
			throw new EncryptDecryptException(e);
		}

		return encryptedText;
	}

	/**
	 * Decrypts the given base64 encoded text using AES CBC PKCS5 padding algorithm.
	 * The security key spec is constructed from the digested hash string derived by applying the given message digest hash algorithm on the given security key token.
	 *
	 * Initialization vector(iv) for CBC algorithm is extracted after decoding the given base64 encoded encrypted text into its original byte format and the cipher block size
	 *
	 * Returns the decrypted text in string format.
	 *
	 * @param base64TextToBeDecrypted, the encrypted text in base64 format that must be used for decryption
	 * @param securityKey, the security key that must be used to decrypt the given text. This must be same as the one used for encryption
	 * @param digestAlgorithm, the message digest hashing algorithm to be applied on the security key string bytes
	 *
	 * @return the actual text after decryption in string format. If failed to decrypt returns an empty string
	 *
	 * @throws EncryptDecryptException, the exception if the encryption algorithm fails due to the cipher block size or padding issue or for any other reason
	 */
	public static String decryptCBCWithRandomIVAndPKCS5Padding(String base64TextToBeDecrypted, String securityKey, String digestAlgorithm)throws EncryptDecryptException {
		String decryptedText = "";
		try {
			decryptedText = decryptWithRandomIV(base64TextToBeDecrypted, getDigestedKey(securityKey, digestAlgorithm));
		} catch (UnsupportedEncodingException | NoSuchAlgorithmException | EncryptDecryptException e) {
			throw new EncryptDecryptException(e);
		}

		return decryptedText;
	}

	/**
	 * Performs AES-GCM encryption on the given text using key and iv byte array
	 *
	 * @param plainText, the text to be encrypted
	 * @param key, the secret key to be used as encryption key
	 * @param ivKeyString, the initialization vector in byte form
	 * @return the encrypted text in base64 encoded format
	 *
	 * @throws EncryptDecryptException, if the encryption fails due to any reason
	 */
	public static String encryptGCMNoPadding(String plainText, SecretKey key, String ivKeyString) throws EncryptDecryptException {
		String encryptedText = "";
		try {
			// Get Cipher Instance
			Cipher cipher = Cipher.getInstance(GCM_WITH_NOPADDING);

			// Create SecretKeySpec
			SecretKeySpec keySpec = new SecretKeySpec(key.getEncoded(), "AES");

			// Create GCMParameterSpec
			GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, getIVKey(ivKeyString));

			// Initialize Cipher for ENCRYPT_MODE
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec);

			byte[] plainTextInBytes = plainText.getBytes();
			// Perform Encryption
			byte[] cipherText = cipher.doFinal(plainTextInBytes);

			encryptedText = Base64.getEncoder().encodeToString(cipherText);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException e) {
			throw new EncryptDecryptException(e);
		}

		return encryptedText;
	}

	/**
	 * Performs AES GCM decryption on the given base64 string using the secret key of size 256 and iv array
	 *
	 * @param base64TextToBeDecrypted, the text in base64 format to be decrypted
	 * @param key, the random secure key to be used for decryption algorithm
	 * @param ivKeyString, the initialization vector to be used during decryption
	 * @return the text after decryption in string format
	 *
	 * @throws EncryptDecryptException, throws the exception if the decryption process fails for any reason
	 */
	public static String decryptGCMNoPadding(String base64TextToBeDecrypted, SecretKey key, String ivKeyString) throws EncryptDecryptException {
		String decryptedText = "";
		try {
			// Get Cipher Instance
			Cipher cipher = Cipher.getInstance(GCM_WITH_NOPADDING);

			// Create SecretKeySpec
			SecretKeySpec keySpec = new SecretKeySpec(key.getEncoded(), "AES");

			// Create GCMParameterSpec
			GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, getIVKey(ivKeyString));

			// Initialize Cipher for DECRYPT_MODE
			cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);

			// Perform Decryption
			byte []cipherText = Base64.getDecoder().decode(base64TextToBeDecrypted);
			byte[] decryptedTextInBytes = cipher.doFinal(cipherText);

			decryptedText = new String(decryptedTextInBytes);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException e) {
			throw new EncryptDecryptException(e);
		}

		return decryptedText;
	}
	
	public static void main (String args[]) {
		
	}

}
