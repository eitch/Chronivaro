package ch.eitchnet.chronivaro.rest;

import ch.eitchnet.chronivaro.rest.dto.AbsenceTypeDto;
import ch.eitchnet.chronivaro.rest.resource.ChronivaroRestHelper;
import com.google.gson.reflect.TypeToken;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class AbsenceTypeResourceTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldPerformCrudOnAbsenceTypes() {
		String authToken = authenticate();

		// Create
		AbsenceTypeDto newType = new AbsenceTypeDto(null, "TEST", "Test Absence", true, false, true, true,
				true, true, List.of("FULL_DAY"), true);
		String json = ChronivaroRestHelper.createGson().toJson(newType);

		try (Response response = target()
				.path("chronivaro/v1/admin/absence-types")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(json))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		String absenceTypeId;
		// Get all
		try (Response response = target()
				.path("chronivaro/v1/admin/absence-types")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			List<AbsenceTypeDto> types = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<AbsenceTypeDto>>() {
					}.getType());
			AbsenceTypeDto created = types.stream().filter(t -> t.name().equals("Test Absence")).findFirst().orElseThrow();
			absenceTypeId = created.id();
			org.junit.Assert.assertTrue(created.commentRequired());
			org.junit.Assert.assertTrue(created.visibleOnPublicStatus());
		}

		// Update
		AbsenceTypeDto updatedType = new AbsenceTypeDto(absenceTypeId, "TEST", "Updated Test Absence", true, false,
				true, true, false, false, List.of("FULL_DAY"), false);
		String updatedJson = ChronivaroRestHelper.createGson().toJson(updatedType);

		try (Response response = target()
				.path("chronivaro/v1/admin/absence-types/" + absenceTypeId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.put(Entity.json(updatedJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Verify update
		try (Response response = target()
				.path("chronivaro/v1/admin/absence-types")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			List<AbsenceTypeDto> types = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<AbsenceTypeDto>>() {
					}.getType());
			AbsenceTypeDto found = types.stream().filter(t -> t.id().equals(absenceTypeId)).findFirst().orElseThrow();
			assertEquals("Updated Test Absence", found.name());
			assertFalse(found.active());
			assertFalse(found.commentRequired());
			assertFalse(found.visibleOnPublicStatus());
		}

		// Delete
		try (Response response = target()
				.path("chronivaro/v1/admin/absence-types/" + absenceTypeId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.delete()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Verify deletion
		try (Response response = target()
				.path("chronivaro/v1/admin/absence-types")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			List<AbsenceTypeDto> types = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<AbsenceTypeDto>>() {
					}.getType());
			assertFalse(types.stream().anyMatch(t -> t.id().equals(absenceTypeId)));
		}
	}
}
