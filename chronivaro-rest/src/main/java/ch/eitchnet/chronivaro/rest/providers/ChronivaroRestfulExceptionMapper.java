package ch.eitchnet.chronivaro.rest.providers;

import ch.eitchnet.chronivaro.rest.dto.ErrorDto;
import ch.eitchnet.chronivaro.rest.dto.FieldErrorDto;
import ch.eitchnet.chronivaro.rest.resource.ChronivaroRestHelper;
import ch.eitchnet.chronivaro.rest.resource.RestException;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import li.strolch.exception.StrolchAccessDeniedException;
import li.strolch.exception.StrolchElementNotFoundException;
import li.strolch.exception.StrolchModelException;
import li.strolch.exception.StrolchUserMessageException;
import li.strolch.privilege.base.AccessDeniedException;
import li.strolch.privilege.base.PrivilegeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import static li.strolch.utils.helper.StringHelper.isEmpty;

@Provider
public class ChronivaroRestfulExceptionMapper implements ExceptionMapper<Throwable> {

	private static final Logger logger = LoggerFactory.getLogger(ChronivaroRestfulExceptionMapper.class);

	@Override
	public Response toResponse(Throwable exception) {
		String correlationId = CorrelationIdFilter.getOrCreateCorrelationId();

		Response.Status status = Response.Status.INTERNAL_SERVER_ERROR;
		String errorCode = "INTERNAL_SERVER_ERROR";
		String message = exception.getMessage();
		List<FieldErrorDto> fieldErrors = Collections.emptyList();

		if (exception instanceof RestException restException) {
			status = restException.getStatus();
			errorCode = restException.getErrorCode();
			message = restException.getMessage();
			fieldErrors = restException.getFieldErrors();
		} else if (exception instanceof StrolchAccessDeniedException || exception instanceof AccessDeniedException
				|| exception instanceof PrivilegeException) {
			status = Response.Status.FORBIDDEN;
			errorCode = "ACCESS_DENIED";
		} else if (exception instanceof StrolchElementNotFoundException) {
			status = Response.Status.NOT_FOUND;
			errorCode = "NOT_FOUND";
		} else if (exception instanceof StrolchUserMessageException) {
			status = Response.Status.BAD_REQUEST;
			errorCode = "USER_ERROR";
		} else if (exception instanceof StrolchModelException) {
			status = Response.Status.BAD_REQUEST;
			errorCode = "MODEL_ERROR";
		} else if (exception instanceof IllegalArgumentException) {
			status = Response.Status.BAD_REQUEST;
			errorCode = "INVALID_ARGUMENT";
		} else if (exception instanceof IllegalStateException) {
			status = Response.Status.BAD_REQUEST;
			errorCode = "ILLEGAL_STATE";
		} else if (exception instanceof JsonSyntaxException || exception instanceof JsonParseException) {
			status = Response.Status.BAD_REQUEST;
			errorCode = "INVALID_JSON";
		} else if (exception instanceof WebApplicationException webAppEx) {
			Response origResponse = webAppEx.getResponse();
			int statusCode = origResponse.getStatus();
			Response.Status statusFromCode = Response.Status.fromStatusCode(statusCode);
			status = statusFromCode != null ? statusFromCode : Response.Status.INTERNAL_SERVER_ERROR;
			errorCode = status.name();
		} else {
			logger.error("Unhandled server exception (correlationId={}): {}", correlationId, exception.getMessage(),
					exception);
		}

		if (isEmpty(message))
			message = "An error occurred: " + errorCode;

		ErrorDto errorDto = new ErrorDto(errorCode, message, fieldErrors, correlationId);
		return Response.status(status)
				.type(MediaType.APPLICATION_JSON)
				.header(CorrelationIdFilter.HEADER_CORRELATION_ID, correlationId)
				.entity(ChronivaroRestHelper.createGson().toJson(errorDto))
				.build();
	}
}
