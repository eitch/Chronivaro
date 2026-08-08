package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.rest.dto.AbsenceTypeDto;
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

public class AbsenceTypeResourceTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldPerformCrudOnAbsenceTypes() {
		String authToken = authenticate();

		// Create
		AbsenceTypeDto newType = new AbsenceTypeDto("test-absence", "TEST", "Test Absence", true, false, true, true,
				List.of("FULL_DAY"), true);
		String json = ChronivaroRestHelper.createGson().toJson(newType);

		try (Response response = target().path("chronivaro/v1/admin/absence-types")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(json))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Get all
		try (Response response = target().path("chronivaro/v1/admin/absence-types")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			List<AbsenceTypeDto> types = ChronivaroRestHelper.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<AbsenceTypeDto>>() {
					}.getType());
			assertTrue(types.stream().anyMatch(t -> t.id().equals("test-absence")));
		}

		// Update
		AbsenceTypeDto updatedType = new AbsenceTypeDto("test-absence", "TEST", "Updated Test Absence", true, false, true,
				true, List.of("FULL_DAY"), false);
		String updatedJson = ChronivaroRestHelper.createGson().toJson(updatedType);

		try (Response response = target().path("chronivaro/v1/admin/absence-types/test-absence")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.put(Entity.json(updatedJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Verify update
		try (Response response = target().path("chronivaro/v1/admin/absence-types")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			List<AbsenceTypeDto> types = ChronivaroRestHelper.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<AbsenceTypeDto>>() {
					}.getType());
			AbsenceTypeDto found = types.stream().filter(t -> t.id().equals("test-absence")).findFirst().orElseThrow();
			assertEquals("Updated Test Absence", found.name());
			assertFalse(found.active());
		}

		// Delete
		try (Response response = target().path("chronivaro/v1/admin/absence-types/test-absence")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.delete()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Verify deletion
		try (Response response = target().path("chronivaro/v1/admin/absence-types")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			List<AbsenceTypeDto> types = ChronivaroRestHelper.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<AbsenceTypeDto>>() {
					}.getType());
			assertFalse(types.stream().anyMatch(t -> t.id().equals("test-absence")));
		}
	}
}
