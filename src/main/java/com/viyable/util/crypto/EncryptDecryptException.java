package com.viyable.util.crypto;

public class EncryptDecryptException extends Exception{

	/**
	 * 
	 */
	private static final long serialVersionUID = 3526218514660903325L;
	
	public EncryptDecryptException (Throwable e) {
		super ("Any of the given text, key token, or the padding is not in the correct format or expected length", e);
	}

}
