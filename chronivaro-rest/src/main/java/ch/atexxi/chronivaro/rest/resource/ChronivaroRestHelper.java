package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.rest.dto.ErrorDto;
import ch.atexxi.chronivaro.rest.dto.FieldErrorDto;
import ch.atexxi.chronivaro.rest.providers.CorrelationIdFilter;
import com.google.gson.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.exception.StrolchAccessDeniedException;
import li.strolch.exception.StrolchElementNotFoundException;
import li.strolch.exception.StrolchModelException;
import li.strolch.exception.StrolchUserMessageException;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.base.AccessDeniedException;
import li.strolch.privilege.base.PrivilegeException;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.RestfulStrolchComponent;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.service.api.ServiceResultState;
import li.strolch.utils.helper.ExceptionHelper;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static li.strolch.utils.helper.StringHelper.isEmpty;

public class ChronivaroRestHelper {

	public static Gson createGson() {
		return new GsonBuilder()
				.registerTypeAdapter(LocalDate.class,
						(JsonSerializer<LocalDate>) (src, typeOfSrc, context) -> new JsonPrimitive(
								src.format(DateTimeFormatter.ISO_LOCAL_DATE)))
				.registerTypeAdapter(LocalDate.class, (JsonDeserializer<LocalDate>) (json, typeOfT, context) -> {
					String s = json.getAsString();
					return isEmpty(s) ? null : LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
				})
				.registerTypeAdapter(YearMonth.class,
						(JsonSerializer<YearMonth>) (src, typeOfSrc, context) -> new JsonPrimitive(src.toString()))
				.registerTypeAdapter(YearMonth.class,
						(JsonDeserializer<YearMonth>) (json, typeOfT, context) -> YearMonth.parse(json.getAsString()))
				.registerTypeAdapter(ZonedDateTime.class,
						(JsonSerializer<ZonedDateTime>) (src, typeOfSrc, context) -> new JsonPrimitive(
								src.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
				.registerTypeAdapter(ZonedDateTime.class,
						(JsonDeserializer<ZonedDateTime>) (json, typeOfT, context) -> ZonedDateTime.parse(
								json.getAsString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME))
				.create();
	}

	public static StrolchTransaction openTx(Certificate cert) {
		return RestfulStrolchComponent.getInstance().openTx(cert, ExceptionHelper.getCallerMethod(2), false);
	}

	public static ServiceHandler getServiceHandler() {
		return RestfulStrolchComponent.getInstance().getComponent(ServiceHandler.class);
	}

	public static Response toResponse(ServiceResult result) {
		if (result.isOk()) {
			JsonObject json = new JsonObject();
			json.addProperty("msg", result.getMessage());
			return Response.ok(createGson().toJson(json), MediaType.APPLICATION_JSON).build();
		}

		Response.Status status = Response.Status.BAD_REQUEST;
		String errorCode = "SERVICE_FAILED";
		Throwable rootCause = result.getRootCause();
		if (rootCause instanceof StrolchAccessDeniedException || rootCause instanceof AccessDeniedException
				|| rootCause instanceof PrivilegeException) {
			status = Response.Status.FORBIDDEN;
			errorCode = "ACCESS_DENIED";
		} else if (rootCause instanceof StrolchElementNotFoundException) {
			status = Response.Status.NOT_FOUND;
			errorCode = "NOT_FOUND";
		} else if (rootCause instanceof StrolchUserMessageException) {
			status = Response.Status.BAD_REQUEST;
			errorCode = "USER_ERROR";
		} else if (rootCause instanceof StrolchModelException) {
			status = Response.Status.BAD_REQUEST;
			errorCode = "MODEL_ERROR";
		} else if (rootCause instanceof IllegalArgumentException) {
			status = Response.Status.BAD_REQUEST;
			errorCode = "INVALID_ARGUMENT";
		} else if (rootCause instanceof IllegalStateException) {
			status = Response.Status.BAD_REQUEST;
			errorCode = "ILLEGAL_STATE";
		} else if (result.getState() == ServiceResultState.EXCEPTION) {
			status = Response.Status.INTERNAL_SERVER_ERROR;
			errorCode = "INTERNAL_SERVER_ERROR";
		}

		String message = result.getMessage();
		if (isEmpty(message) && rootCause != null)
			message = rootCause.getMessage();
		if (isEmpty(message))
			message = "Service execution failed";

		return toErrorResponse(status, errorCode, message);
	}

	public static Response toErrorResponse(Response.Status status, String errorCode, String message) {
		return toErrorResponse(status, new ErrorDto(errorCode, message, CorrelationIdFilter.getOrCreateCorrelationId()));
	}

	public static Response toErrorResponse(Response.Status status, String errorCode, String message,
			List<FieldErrorDto> fieldErrors) {
		return toErrorResponse(status,
				new ErrorDto(errorCode, message, fieldErrors, CorrelationIdFilter.getOrCreateCorrelationId()));
	}

	public static Response toErrorResponse(Response.Status status, ErrorDto errorDto) {
		String correlationId = errorDto.correlationId();
		if (isEmpty(correlationId))
			correlationId = CorrelationIdFilter.getOrCreateCorrelationId();

		return Response.status(status)
				.type(MediaType.APPLICATION_JSON)
				.header(CorrelationIdFilter.HEADER_CORRELATION_ID, correlationId)
				.entity(createGson().toJson(errorDto))
				.build();
	}
}
