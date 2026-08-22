package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.rest.dto.UserDto;
import ch.atexxi.chronivaro.rest.resource.ChronivaroRestHelper;
import com.google.gson.reflect.TypeToken;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class UserResourceTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldPerformCrudAndRegistrationOnUsers() {
		String authToken = authenticate();

		// 1. Get all users
		try (Response response = target()
				.path("chronivaro/v1/admin/users")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			List<UserDto> users = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<UserDto>>() {
					}.getType());
			assertNotNull(users);
			assertTrue(users.stream().anyMatch(u -> "admin".equals(u.username())));
			assertFalse("System users must not be returned", users.stream().anyMatch(u -> "agent".equals(u.username())));
			assertFalse("Users with SYSTEM state must not be returned", users.stream().anyMatch(u -> "SYSTEM".equalsIgnoreCase(u.state())));
		}

		// Verify system user (agent) cannot be retrieved directly
		try (Response response = target()
				.path("chronivaro/v1/admin/users/agent")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
		}

		// 2. Create pure user
		UserDto newUser = new UserDto(null, "purehruser", "Pure", "HRUser", "purehr@example.com",
				Set.of("HR", "Supervisor"), "ENABLED", "de", false, null, null);
		String json = ChronivaroRestHelper.createGson().toJson(newUser);

		String createdUserId;
		try (Response response = target()
				.path("chronivaro/v1/admin/users")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(json))) {
			assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
			UserDto created = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), UserDto.class);
			assertNotNull(created.id());
			assertEquals("purehruser", created.username());
			assertEquals("Pure", created.firstname());
			assertEquals("HRUser", created.lastname());
			assertEquals("purehr@example.com", created.email());
			assertTrue(created.roles().contains("HR"));
			assertTrue(created.roles().contains("Supervisor"));
			assertFalse(created.hasLinkedEmployee());
			createdUserId = created.id();
		}

		// 3. Get by ID
		try (Response response = target()
				.path("chronivaro/v1/admin/users/" + createdUserId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			UserDto user = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), UserDto.class);
			assertEquals("purehruser", user.username());
			assertEquals("Pure", user.firstname());
		}

		// 4. Update user
		UserDto updateUser = new UserDto(createdUserId, "purehruser", "UpdatedPure", "HRDirector", "director@example.com",
				Set.of("HR", "Administrator"), "DISABLED", "en", false, null, null);
		String updateJson = ChronivaroRestHelper.createGson().toJson(updateUser);

		try (Response response = target()
				.path("chronivaro/v1/admin/users/" + createdUserId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.put(Entity.json(updateJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			UserDto updated = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), UserDto.class);
			assertEquals("UpdatedPure", updated.firstname());
			assertEquals("HRDirector", updated.lastname());
			assertEquals("director@example.com", updated.email());
			assertEquals("DISABLED", updated.state());
			assertTrue(updated.roles().contains("Administrator"));
		}

		// 5. Initiate Registration Challenge
		try (Response response = target()
				.path("chronivaro/v1/admin/users/" + createdUserId + "/register")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(""))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// 6. Set Password
		String passwordJson = "{\"password\":\"" + Base64.getEncoder().encodeToString("NewPassword123!".getBytes()) + "\"}";
		try (Response response = target()
				.path("strolch/privilege/users/" + createdUserId + "/password")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.put(Entity.json(passwordJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}
	}
}
