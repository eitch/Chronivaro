package ch.eitchnet.chronivaro.rest;

import ch.eitchnet.chronivaro.rest.dto.ConfigurationDto;
import ch.eitchnet.chronivaro.rest.resource.ChronivaroRestHelper;
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
			assertEquals("Chronivaro", dto.companyName());
			assertEquals("de", dto.defaultLanguage());
			assertNotNull(dto.version());
		}
	}

	@Test
	public void shouldGetPublicBranding() {
		try (Response response = target()
				.path("chronivaro/v1/system/branding")
				.request(MediaType.APPLICATION_JSON)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			String body = response.readEntity(String.class);
			ch.eitchnet.chronivaro.rest.dto.BrandingDto dto = ChronivaroRestHelper.createGson().fromJson(body, ch.eitchnet.chronivaro.rest.dto.BrandingDto.class);
			assertNotNull(dto);
			assertNotNull(dto.companyName());
			assertNotNull(dto.defaultLanguage());
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
				  "vacationAbsenceTypeCode": "VACATION",
				  "companyName": "Acme Time Corp",
				  "companyLogo": "https://example.com/logo.png",
				  "defaultLanguage": "en"
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
			assertEquals("Acme Time Corp", updated.companyName());
			assertEquals("https://example.com/logo.png", updated.companyLogo());
			assertEquals("en", updated.defaultLanguage());
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

	@Test
	public void shouldUploadRetrieveAndDeleteLogo() {
		String authToken = authenticate();

		String validPngDataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

		// 1. Upload logo via POST /admin/configuration/logo with JSON payload
		String uploadPayload = "{\"companyLogo\": \"" + validPngDataUri + "\"}";
		try (Response response = target()
				.path("chronivaro/v1/admin/configuration/logo")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(uploadPayload))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			ConfigurationDto updated = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), ConfigurationDto.class);
			assertEquals(validPngDataUri, updated.companyLogo());
		}

		// 2. Retrieve logo via GET /admin/configuration/logo
		try (Response response = target()
				.path("chronivaro/v1/admin/configuration/logo")
				.request("image/png")
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			assertTrue("Must be image/png media type", response.getMediaType().isCompatible(MediaType.valueOf("image/png")));
			byte[] bytes = response.readEntity(byte[].class);
			assertTrue("Returned image bytes must not be empty", bytes.length > 0);
		}

		// 3. Retrieve public branding logo via GET /system/branding/logo (unauthenticated)
		try (Response response = target()
				.path("chronivaro/v1/system/branding/logo")
				.request("image/png")
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			assertTrue("Must be image/png media type", response.getMediaType().isCompatible(MediaType.valueOf("image/png")));
			byte[] bytes = response.readEntity(byte[].class);
			assertTrue("Returned image bytes must not be empty", bytes.length > 0);
		}

		// 4. Upload raw PNG bytes directly
		byte[] rawPng = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
		try (Response response = target()
				.path("chronivaro/v1/admin/configuration/logo")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.entity(rawPng, "image/png"))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			ConfigurationDto updated = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), ConfigurationDto.class);
			assertTrue("Logo must be data URI format", updated.companyLogo().startsWith("data:image/png;base64,"));
		}

		// 5. Delete logo via DELETE /admin/configuration/logo
		try (Response response = target()
				.path("chronivaro/v1/admin/configuration/logo")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.delete()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			ConfigurationDto updated = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), ConfigurationDto.class);
			assertEquals("", updated.companyLogo());
		}

		// 6. Verify GET /admin/configuration/logo returns 404 NOT_FOUND after deletion
		try (Response response = target()
				.path("chronivaro/v1/admin/configuration/logo")
				.request("image/png")
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
		}
	}
}
