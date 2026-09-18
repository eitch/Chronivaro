package ch.eitchnet.chronivaro.rest;

import ch.eitchnet.chronivaro.rest.dto.PersonalAccessTokenCreatedDto;
import ch.eitchnet.chronivaro.rest.dto.PersonalAccessTokenDto;
import ch.eitchnet.chronivaro.rest.dto.TimerStatusDto;
import ch.eitchnet.chronivaro.rest.resource.ChronivaroRestHelper;
import com.google.gson.reflect.TypeToken;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.Assert.*;

public class PersonalAccessTokenResourceTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldManagePersonalAccessTokens() {
		// 1. Authenticate as normal user / employee (e.g. employee)
		String userAuth = authenticate("employee", "admin");

		// 2. List tokens initially (should be empty)
		try (Response response = target()
				.path("chronivaro/v1/me/tokens")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", userAuth)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			List<PersonalAccessTokenDto> tokens = ChronivaroRestHelper.createGson().fromJson(
					response.readEntity(String.class),
					new TypeToken<List<PersonalAccessTokenDto>>() {
					}.getType()
			);
			assertNotNull(tokens);
			assertTrue(tokens.isEmpty());
		}

		// 3. Create a token with DESKTOP_TIMER preset
		String createJson = """
				{
				  "name": "Desktop Timer Mac",
				  "preset": "DESKTOP_TIMER",
				  "validTo": "2027-09-17T23:59:59+02:00"
				}
				""";
		String desktopTokenSecret;
		String desktopTokenId;
		try (Response response = target()
				.path("chronivaro/v1/me/tokens")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", userAuth)
				.post(Entity.json(createJson))) {
			String entity = response.readEntity(String.class);
			assertEquals("Got " + response.getStatus() + ": " + entity, Response.Status.CREATED.getStatusCode(), response.getStatus());
			PersonalAccessTokenCreatedDto created = ChronivaroRestHelper.createGson().fromJson(
					entity,
					PersonalAccessTokenCreatedDto.class
			);
			assertNotNull(created);
			assertNotNull(created.tokenId());
			assertEquals("employee", created.username());
			assertEquals("Desktop Timer Mac", created.name());
			assertEquals("DESKTOP_TIMER", created.preset());
			assertNotNull(created.token());
			assertTrue(created.token().contains(":"));
			desktopTokenId = created.tokenId();
			desktopTokenSecret = created.token();
		}

		// 4. Create another token with FULL_PERSONAL preset and explicit null validTo (should default to 1 year and not throw server error)
		String createJson2 = """
				{
				  "name": "Full Access CLI",
				  "preset": "FULL_PERSONAL",
				  "validTo": null
				}
				""";
		String fullTokenId;
		try (Response response = target()
				.path("chronivaro/v1/me/tokens")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", userAuth)
				.post(Entity.json(createJson2))) {
			assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
			PersonalAccessTokenCreatedDto created = ChronivaroRestHelper.createGson().fromJson(
					response.readEntity(String.class),
					PersonalAccessTokenCreatedDto.class
			);
			assertNotNull(created);
			assertNotNull(created.validTo());
			fullTokenId = created.tokenId();
			assertEquals("FULL_PERSONAL", created.preset());
		}

		// 5. List my tokens
		try (Response response = target()
				.path("chronivaro/v1/me/tokens")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", userAuth)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			List<PersonalAccessTokenDto> tokens = ChronivaroRestHelper.createGson().fromJson(
					response.readEntity(String.class),
					new TypeToken<List<PersonalAccessTokenDto>>() {
					}.getType()
			);
			assertEquals(2, tokens.size());
			assertTrue(tokens.stream().anyMatch(t -> t.tokenId().equals(desktopTokenId)));
			assertTrue(tokens.stream().anyMatch(t -> t.tokenId().equals(fullTokenId)));
		}

		// 6. Use the created token for API request (e.g. GET /me/timer/status with Bearer <tokenId>:<tokenValue>)
		try (Response response = target()
				.path("chronivaro/v1/me/timer/status")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + desktopTokenSecret)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			TimerStatusDto status = ChronivaroRestHelper.createGson().fromJson(
					response.readEntity(String.class),
					TimerStatusDto.class
			);
			assertNotNull(status);
		}

		// 7. Admin lists user's tokens
		String adminAuth = authenticate("admin", "admin");
		try (Response response = target()
				.path("chronivaro/v1/admin/users/employee/tokens")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminAuth)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			List<PersonalAccessTokenDto> tokens = ChronivaroRestHelper.createGson().fromJson(
					response.readEntity(String.class),
					new TypeToken<List<PersonalAccessTokenDto>>() {
					}.getType()
			);
			assertEquals(2, tokens.size());
		}

		// 8. Admin deletes one user token
		try (Response response = target()
				.path("chronivaro/v1/admin/users/employee/tokens/" + fullTokenId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminAuth)
				.delete()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// 9. User deletes their own remaining token
		try (Response response = target()
				.path("chronivaro/v1/me/tokens/" + desktopTokenId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", userAuth)
				.delete()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// 10. List tokens again (should now be empty)
		try (Response response = target()
				.path("chronivaro/v1/me/tokens")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", userAuth)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			List<PersonalAccessTokenDto> tokens = ChronivaroRestHelper.createGson().fromJson(
					response.readEntity(String.class),
					new TypeToken<List<PersonalAccessTokenDto>>() {
					}.getType()
			);
			assertTrue(tokens.isEmpty());
		}

		// 11. Verify employee cannot access another user's tokens (PrivilegePersonalAccessTokenUser check)
		try (Response response = target()
				.path("chronivaro/v1/admin/users/admin/tokens")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", userAuth)
				.get()) {
			assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
		}
	}
}
