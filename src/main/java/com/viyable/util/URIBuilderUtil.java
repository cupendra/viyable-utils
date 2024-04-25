package com.viyable.util;

public class URIBuilderUtil {
	
	static final String LOCAL_SERVER_PATH = "http://localhost:8080/farm";
	
	static final String LOCAL_PATH = "http://localhost:8080";
	
	static final String PUBLIC_PATH = "http://www.viyable.in/farm";
	
	static final String HOME_PATH = LOCAL_PATH;
	
	public static final String buildAccountEmailVerificationPath(String uuid){
		return HOME_PATH + "/ua/verify?evc=" + uuid;
	}
	
	public static final String buildPasswordResetPath(String uuid){
		return HOME_PATH + "/ua/snp?token=" + uuid;
	}
	
	public static final String buildOrgVaultResetPath(Long orgId, String uuid){
		return HOME_PATH + "/org/" + orgId + "/vault-reset?token=" + uuid;
	}
	

}
