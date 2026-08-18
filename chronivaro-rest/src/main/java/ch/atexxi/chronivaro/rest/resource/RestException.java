package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.rest.dto.FieldErrorDto;
import jakarta.ws.rs.core.Response;

import java.util.Collections;
import java.util.List;

public class RestException extends RuntimeException {

	private final Response.Status status;
	private final String errorCode;
	private final List<FieldErrorDto> fieldErrors;

	public RestException(Response.Status status, String errorCode, String message) {
		this(status, errorCode, message, Collections.emptyList(), null);
	}

	public RestException(Response.Status status, String errorCode, String message, List<FieldErrorDto> fieldErrors) {
		this(status, errorCode, message, fieldErrors, null);
	}

	public RestException(Response.Status status, String errorCode, String message, List<FieldErrorDto> fieldErrors,
			Throwable cause) {
		super(message, cause);
		this.status = status;
		this.errorCode = errorCode;
		this.fieldErrors = fieldErrors == null ? Collections.emptyList() : fieldErrors;
	}

	public Response.Status getStatus() {
		return this.status;
	}

	public String getErrorCode() {
		return this.errorCode;
	}

	public List<FieldErrorDto> getFieldErrors() {
		return this.fieldErrors;
	}
}
