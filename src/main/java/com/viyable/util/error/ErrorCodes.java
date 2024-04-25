package com.viyable.util.error;

public class ErrorCodes {
	//Unknown errors 801 - 899
	public static final Integer ERROR_WHILE_SAVING = 801;
	
	//Security errors 901 - 999
	public static final Integer OPERATION_NOT_ALLOWED = 901;
	public static final Integer INVALID_TOKEN = 902;
	
	//User Errors 1001 - 1999
	public static final Integer USER_NOT_FOUND = 1001;
	public static final Integer EXPIRED_PWD_RESET_TOKEN = 1002;
	
	
	//Organization Errors 2001 - 2999
	public static final Integer ORG_VAULT_NOT_FOUND = 2001;
	
	
	//Product Errors 3001 - 3999
	public static final Integer INVALID_PRODUCT_CATEGORY = 3001;
	
	
	//Employee Errors 3001 - 3999
	public static final Integer NO_EMPLOYEES_FOUND = 3001;
	public static final Integer NO_ELIGIBLE_EMPLOYEES_FOUND = 3002;
	public static final Integer SALARY_REPORT_ALREADY_GENERATED = 3003;
	public static final Integer ATTENDANCE_NOT_SUBMITTED = 3004;
	
	//Generic Errors 101 - 199
	public static final Integer NO_DATA_FOUND = 101;
	public static final Integer VALID_DATA_NOT_FOUND = 102;
	public static final Integer INVALID_INPUT_DATA = 103;
	public static final Integer ALREADY_COMPLETED = 104;

}
