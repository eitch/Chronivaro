package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.rest.dto.ErrorDto;
import ch.atexxi.chronivaro.rest.dto.FieldErrorDto;
import ch.atexxi.chronivaro.rest.providers.ChronivaroRestfulExceptionMapper;
import ch.atexxi.chronivaro.rest.providers.CorrelationIdFilter;
import ch.atexxi.chronivaro.rest.resource.ChronivaroRestHelper;
import ch.atexxi.chronivaro.rest.resource.RestException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class RestErrorTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldPropagateClientCorrelationIdInHeaderAndResponse() {
		String authToken = authenticate();
		String customCorrelationId = "custom-corr-id-12345";

		try (Response response = target()
				.path("chronivaro/v1/admin/holiday-calendars")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.header(CorrelationIdFilter.HEADER_CORRELATION_ID, customCorrelationId)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			assertEquals(customCorrelationId, response.getHeaderString(CorrelationIdFilter.HEADER_CORRELATION_ID));
		}
	}

	@Test
	public void shouldGenerateCorrelationIdWhenMissing() {
		String authToken = authenticate();

		try (Response response = target()
				.path("chronivaro/v1/admin/holiday-calendars")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			String correlationId = response.getHeaderString(CorrelationIdFilter.HEADER_CORRELATION_ID);
			assertNotNull(correlationId);
			assertFalse(correlationId.isBlank());
		}
	}

	@Test
	public void shouldReturnStandardErrorDtoOnNotFound() {
		String authToken = authenticate();
		String customCorrelationId = "test-404-corr-id";

		try (Response response = target()
				.path("chronivaro/v1/admin/employees/non-existent-employee-999")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.header(CorrelationIdFilter.HEADER_CORRELATION_ID, customCorrelationId)
				.get()) {
			assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
			assertEquals(customCorrelationId, response.getHeaderString(CorrelationIdFilter.HEADER_CORRELATION_ID));

			String body = response.readEntity(String.class);
			ErrorDto errorDto = ChronivaroRestHelper.createGson().fromJson(body, ErrorDto.class);
			assertNotNull(errorDto);
			assertEquals("NOT_FOUND", errorDto.errorCode());
			assertEquals(customCorrelationId, errorDto.correlationId());
			assertNotNull(errorDto.message());
			assertNotNull(errorDto.fieldErrors());
			assertTrue(errorDto.fieldErrors().isEmpty());
		}
	}

	@Test
	public void shouldReturnStandardErrorDtoOnInvalidJson() {
		String authToken = authenticate();
		String customCorrelationId = "test-invalid-json-corr-id";

		try (Response response = target()
				.path("chronivaro/v1/admin/employees")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.header(CorrelationIdFilter.HEADER_CORRELATION_ID, customCorrelationId)
				.post(Entity.json("{ invalid json format }"))) {
			assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
			assertEquals(customCorrelationId, response.getHeaderString(CorrelationIdFilter.HEADER_CORRELATION_ID));

			String body = response.readEntity(String.class);
			ErrorDto errorDto = ChronivaroRestHelper.createGson().fromJson(body, ErrorDto.class);
			assertNotNull(errorDto);
			assertEquals("INVALID_JSON", errorDto.errorCode());
			assertEquals(customCorrelationId, errorDto.correlationId());
			assertNotNull(errorDto.message());
		}
	}

	@Test
	public void shouldReturnCorrelationIdOnUnauthorizedRequest() {
		try (Response response = target()
				.path("chronivaro/v1/admin/employees")
				.request(MediaType.APPLICATION_JSON)
				.get()) {
			assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
			String correlationId = response.getHeaderString(CorrelationIdFilter.HEADER_CORRELATION_ID);
			assertNotNull(correlationId);
			assertFalse(correlationId.isBlank());
		}
	}

	@Test
	public void shouldMapRestExceptionWithFieldErrors() {
		CorrelationIdFilter.setCorrelationId("test-field-errors-corr");
		try {
			List<FieldErrorDto> fieldErrors = List.of(
					new FieldErrorDto("start", "OVERLAP"),
					new FieldErrorDto("end", "BEFORE_START")
			);
			RestException restException = new RestException(
					Response.Status.BAD_REQUEST,
					"WORK_ENTRY_OVERLAP",
					"The work entry overlaps with an existing entry.",
					fieldErrors
			);

			ChronivaroRestfulExceptionMapper mapper = new ChronivaroRestfulExceptionMapper();
			try (Response response = mapper.toResponse(restException)) {
				assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
				assertEquals("test-field-errors-corr", response.getHeaderString(CorrelationIdFilter.HEADER_CORRELATION_ID));

				String body = (String) response.getEntity();
				ErrorDto errorDto = ChronivaroRestHelper.createGson().fromJson(body, ErrorDto.class);
				assertNotNull(errorDto);
				assertEquals("WORK_ENTRY_OVERLAP", errorDto.errorCode());
				assertEquals("The work entry overlaps with an existing entry.", errorDto.message());
				assertEquals("test-field-errors-corr", errorDto.correlationId());
				assertEquals(2, errorDto.fieldErrors().size());
				assertEquals("start", errorDto.fieldErrors().get(0).field());
				assertEquals("OVERLAP", errorDto.fieldErrors().get(0).code());
				assertEquals("end", errorDto.fieldErrors().get(1).field());
				assertEquals("BEFORE_START", errorDto.fieldErrors().get(1).code());
			}
		} finally {
			CorrelationIdFilter.removeCorrelationId();
		}
	}
}
