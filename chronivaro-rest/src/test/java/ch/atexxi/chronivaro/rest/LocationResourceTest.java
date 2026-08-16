package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.rest.dto.LocationDto;
import ch.atexxi.chronivaro.rest.resource.ChronivaroRestHelper;
import com.google.gson.reflect.TypeToken;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class LocationResourceTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldPerformCrudOnLocations() {
		String authToken = authenticate();

		// Create
		LocationDto newLocation = new LocationDto(null, "Test Location", "Europe/Zurich", "calendar-1", "calendar-1");
		String json = ChronivaroRestHelper.createGson().toJson(newLocation);

		try (Response response = target()
				.path("chronivaro/v1/admin/locations")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(json))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		String locationId;
		// Get all
		try (Response response = target()
				.path("chronivaro/v1/admin/locations")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			List<LocationDto> locations = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<LocationDto>>() {
					}.getType());
			locationId = locations
					.stream()
					.filter(l -> l.name().equals("Test Location"))
					.findFirst()
					.orElseThrow()
					.id();
		}

		// Update
		LocationDto updatedLocation = new LocationDto(locationId, "Updated Test Location", "Europe/Zurich",
				"calendar-2", "calendar-2");
		String updatedJson = ChronivaroRestHelper.createGson().toJson(updatedLocation);

		try (Response response = target()
				.path("chronivaro/v1/admin/locations/" + locationId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.put(Entity.json(updatedJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Verify update
		try (Response response = target()
				.path("chronivaro/v1/admin/locations")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			List<LocationDto> locations = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<LocationDto>>() {
					}.getType());
			LocationDto found = locations.stream().filter(l -> l.id().equals(locationId)).findFirst().orElseThrow();
			assertEquals("Updated Test Location", found.name());
			assertEquals("calendar-2", found.holidayCalendarId());
		}

		// Delete
		try (Response response = target()
				.path("chronivaro/v1/admin/locations/" + locationId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.delete()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Verify deletion
		try (Response response = target()
				.path("chronivaro/v1/admin/locations")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			List<LocationDto> locations = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<LocationDto>>() {
					}.getType());
			assertFalse(locations.stream().anyMatch(l -> l.id().equals(locationId)));
		}
	}
}
