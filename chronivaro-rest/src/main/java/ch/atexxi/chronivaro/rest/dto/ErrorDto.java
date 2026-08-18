package ch.atexxi.chronivaro.rest.dto;

import java.util.Collections;
import java.util.List;

public record ErrorDto(String errorCode, String message, List<FieldErrorDto> fieldErrors, String correlationId) {

	public ErrorDto(String errorCode, String message, String correlationId) {
		this(errorCode, message, Collections.emptyList(), correlationId);
	}
}
