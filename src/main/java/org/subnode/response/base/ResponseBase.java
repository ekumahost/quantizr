package org.subnode.response.base;

import org.subnode.util.ThreadLocals;

public class ResponseBase {
	private boolean success;
	private String message;
	private String stackTrace;
	private String exceptionClass;

	//for now only 'auth' is available here (will make an enum eventually)
	private String exceptionType;

	public ResponseBase() {
		ThreadLocals.setResponse(this);
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getStackTrace() {
		return this.stackTrace;
	}

	public void setStackTrace(String stackTrace) {
		this.stackTrace = stackTrace;
	}

	public String getExceptionClass() {
		return exceptionClass;
	}

	public void setExceptionClass(String exceptionClass) {
		this.exceptionClass = exceptionClass;
	}

	public String getExceptionType() {
		return exceptionType;
	}

	public void setExceptionType(String exceptionType) {
		this.exceptionType = exceptionType;
	}
}
