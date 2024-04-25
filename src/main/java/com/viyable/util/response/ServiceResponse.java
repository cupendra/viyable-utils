package com.viyable.util.response;

public class ServiceResponse {
	
	private Integer resultCode;
	
	private String message;
	
	private Object result;
	
	private Integer errorCode;
	
	public static ServiceResponse success(){
		ServiceResponse response = new ServiceResponse();
		response.setResultCode(ResponseCodes.SUCCESS);
		
		return response;
	}
	
	public static ServiceResponse success(Object result){
		ServiceResponse response = new ServiceResponse();
		response.setResultCode(ResponseCodes.SUCCESS);
		response.setResult(result);
		
		return response;
	}
	
	public static ServiceResponse failure(Integer errorCode){
		ServiceResponse response = new ServiceResponse();
		response.setResultCode(ResponseCodes.FAILURE);
		response.setErrorCode(errorCode);
		
		return response;
	}
	
	public void markFailed(Integer errorCode){
		setResultCode(ResponseCodes.FAILURE);
		setErrorCode(errorCode);
	}
	
	public boolean isSuccess(){
		return resultCode != null && resultCode.equals(ResponseCodes.SUCCESS);
	}
	
	public boolean isFailed(){
		return resultCode != null && resultCode.equals(ResponseCodes.FAILURE);
	}

	public Integer getResultCode() {
		return resultCode;
	}

	public String getMessage() {
		return message;
	}

	public Integer getErrorCode() {
		return errorCode;
	}

	public void setResultCode(Integer resultCode) {
		this.resultCode = resultCode;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public void setErrorCode(Integer errorCode) {
		this.errorCode = errorCode;
	}

	public Object getResult() {
		return result;
	}

	public void setResult(Object result) {
		this.result = result;
	}

}
