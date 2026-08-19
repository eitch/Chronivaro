package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.rest.dto.ConfigurationDto;
import ch.atexxi.chronivaro.rest.resource.ChronivaroRestHelper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import static org.junit.Assert.*;

public class ConfigurationResourceTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldGetConfigurationWithETag() {
		String authToken = authenticate();

		try (Response response = target()
				.path("chronivaro/v1/admin/configuration")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			String etag = response.getHeaderString("ETag");
			assertNotNull("ETag header must be present", etag);

			String body = response.readEntity(String.class);
			ConfigurationDto dto = ChronivaroRestHelper.createGson().fromJson(body, ConfigurationDto.class);
			assertNotNull(dto);
			assertNotNull(dto.weeklyTargetMinutes());
			assertNotNull(dto.annualVacationDays());
			assertNotNull(dto.minutesPerVacationDay());
			assertNotNull(dto.vacationAbsenceTypeCode());
			assertNotNull(dto.version());
		}
	}

	@Test
	public void shouldUpdateConfigurationWithIfMatch() {
		String authToken = authenticate();

		String currentEtag;
		ConfigurationDto initial;
		try (Response response = target()
				.path("chronivaro/v1/admin/configuration")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			currentEtag = response.getHeaderString("ETag");
			assertNotNull(currentEtag);
			initial = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), ConfigurationDto.class);
		}

		String updateJson = """
				{
				  "weeklyTargetMinutes": 2400,
				  "annualVacationDays": 28,
				  "minutesPerVacationDay": 480,
				  "vacationAbsenceTypeCode": "VACATION"
				}
				""";

		try (Response response = target()
				.path("chronivaro/v1/admin/configuration")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.header("If-Match", currentEtag)
				.put(Entity.json(updateJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			String newEtag = response.getHeaderString("ETag");
			assertNotNull(newEtag);
			assertNotEquals(currentEtag, newEtag);

			ConfigurationDto updated = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), ConfigurationDto.class);
			assertEquals(Integer.valueOf(2400), updated.weeklyTargetMinutes());
			assertEquals(Integer.valueOf(28), updated.annualVacationDays());
			assertEquals(Integer.valueOf(480), updated.minutesPerVacationDay());
			assertEquals("VACATION", updated.vacationAbsenceTypeCode());
			assertEquals(Integer.valueOf(initial.version() + 1), updated.version());
			assertEquals("admin", updated.updatedBy());
		}
	}

	@Test
	public void shouldRejectUpdateOnIfMatchConflict() {
		String authToken = authenticate();

		String updateJson = """
				{
				  "weeklyTargetMinutes": 2400,
				  "annualVacationDays": 28,
				  "minutesPerVacationDay": 480,
				  "vacationAbsenceTypeCode": "VACATION"
				}
				""";

		try (Response response = target()
				.path("chronivaro/v1/admin/configuration")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.header("If-Match", "99999")
				.put(Entity.json(updateJson))) {
			assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
		}
	}

	@Test
	public void shouldRejectInvalidConfigurationPayload() {
		String authToken = authenticate();

		String currentEtag;
		try (Response response = target()
				.path("chronivaro/v1/admin/configuration")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			currentEtag = response.getHeaderString("ETag");
		}

		String invalidJson = """
				{
				  "weeklyTargetMinutes": -50,
				  "annualVacationDays": 25,
				  "minutesPerVacationDay": 480,
				  "vacationAbsenceTypeCode": "VACATION"
				}
				""";

		try (Response response = target()
				.path("chronivaro/v1/admin/configuration")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.header("If-Match", currentEtag)
				.put(Entity.json(invalidJson))) {
			assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
		}
	}

	@Test
	public void shouldDenyNonAdminUsers() {
		// Try accessing as unauthenticated user
		try (Response response = target()
				.path("chronivaro/v1/admin/configuration")
				.request(MediaType.APPLICATION_JSON)
				.get()) {
			assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
		}

		// Try updating as unauthenticated user
		try (Response response = target()
				.path("chronivaro/v1/admin/configuration")
				.request(MediaType.APPLICATION_JSON)
				.put(Entity.json("{}"))) {
			assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
		}
	}
}
