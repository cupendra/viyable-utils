package com.viyable.util;

import java.util.Random;
import java.util.UUID;

public class UniqueKeyUtil {
	
	public static void main (String[] args) {
		UUID aUid = UUID.randomUUID();
		String uid = aUid.toString();
		System.out.println("Uid from string: "+uid + ", variant: "+aUid.variant() + ", version: "+aUid.version());
		
		aUid = UUID.nameUUIDFromBytes(("Upendra" + System.currentTimeMillis()).getBytes());
		String anotherUid = aUid.toString();
		System.out.println("Another UID: "+anotherUid + ", variant: "+aUid.variant() + ", version: "+aUid.version());
		
	}
	
	public static String uniqueUUIDByNameTimestampInMillisAndLongRandom(String name) {
		return UUID.nameUUIDFromBytes((new Random().nextLong() + name + System.currentTimeMillis()).getBytes()).toString();
	}
	
	public static String uniqueUUIDByNameTimestampInMillis(String name) {
		return UUID.nameUUIDFromBytes((name + System.currentTimeMillis()).getBytes()).toString();
	}
	
	public static String randomUUID() {
		return UUID.randomUUID().toString();
	}

}
