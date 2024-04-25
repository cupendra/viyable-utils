package com.viyable.util.crypto;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;

public class CryptoUtil {
	
	private static Key getKey (String saltKey) {
		SecretKeySpec secretKeySpec = null;
		try {
			byte[] key = saltKey.getBytes("UTF-8");
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
				Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				Key key = getKey(saltKey);
				cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(new byte[16]));
				
				byte []result = cipher.doFinal(textToBeEncryted.getBytes("UTF-8"));
				encryptedText = Base64.getEncoder().encodeToString(result);
			} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
				e.printStackTrace();
			} catch (InvalidKeyException e) {
				e.printStackTrace();
			} catch (IllegalBlockSizeException e) {
				e.printStackTrace();
			} catch (BadPaddingException e) {
				e.printStackTrace();
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			} catch (InvalidAlgorithmParameterException e) {
				e.printStackTrace();
			}
		}
		
		
		return encryptedText;
	}
	
	public static String decrypt (String textToBeDecryted, String saltKey) {
		String decryptedText = null;
		if (textToBeDecryted != null) {
			try {
				Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				Key key = getKey(saltKey);
				cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(new byte[16]));
				
				byte []enbytes = Base64.getDecoder().decode(textToBeDecryted);
				
				byte []result = cipher.doFinal(enbytes);
				
				decryptedText = new String(result, "UTF-8");
			} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
				e.printStackTrace();
			} catch (InvalidKeyException e) {
				e.printStackTrace();
			} catch (IllegalBlockSizeException e) {
				e.printStackTrace();
			} catch (BadPaddingException e) {
				e.printStackTrace();
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			} catch (InvalidAlgorithmParameterException e) {
				e.printStackTrace();
			}
		}
		
		
		return decryptedText;
	}
	
	public static void main (String args[]) {
		
	}

}
