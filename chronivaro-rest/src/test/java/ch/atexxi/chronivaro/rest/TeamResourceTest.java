package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.rest.dto.TeamDto;
import ch.atexxi.chronivaro.rest.resource.ChronivaroRestHelper;
import com.google.gson.reflect.TypeToken;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TeamResourceTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldPerformCrudOnTeams() {
		String authToken = authenticate();

		// Create
		TeamDto newTeam = new TeamDto("test-team", "Test Team");
		String json = ChronivaroRestHelper.createGson().toJson(newTeam);

		try (Response response = target().path("chronivaro/v1/admin/teams")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(json))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Get all
		try (Response response = target().path("chronivaro/v1/admin/teams")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			List<TeamDto> teams = ChronivaroRestHelper.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<TeamDto>>() {
					}.getType());
			assertTrue(teams.stream().anyMatch(t -> t.id().equals("test-team")));
		}

		// Update
		TeamDto updatedTeam = new TeamDto("test-team", "Updated Test Team");
		String updatedJson = ChronivaroRestHelper.createGson().toJson(updatedTeam);

		try (Response response = target().path("chronivaro/v1/admin/teams/test-team")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.put(Entity.json(updatedJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Verify update
		try (Response response = target().path("chronivaro/v1/admin/teams")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			List<TeamDto> teams = ChronivaroRestHelper.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<TeamDto>>() {
					}.getType());
			TeamDto found = teams.stream().filter(t -> t.id().equals("test-team")).findFirst().orElseThrow();
			assertEquals("Updated Test Team", found.name());
		}

		// Delete
		try (Response response = target().path("chronivaro/v1/admin/teams/test-team")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.delete()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Verify deletion
		try (Response response = target().path("chronivaro/v1/admin/teams")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			List<TeamDto> teams = ChronivaroRestHelper.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<TeamDto>>() {
					}.getType());
			assertFalse(teams.stream().anyMatch(t -> t.id().equals("test-team")));
		}
	}
}
