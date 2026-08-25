package ch.eitchnet.chronivaro.rest;

import ch.eitchnet.chronivaro.rest.dto.HolidayCalendarDto;
import ch.eitchnet.chronivaro.rest.dto.HolidayDto;
import ch.eitchnet.chronivaro.rest.resource.ChronivaroRestHelper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class HolidayCalendarResourceTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldCreateHolidayCalendarAndHoliday() {
		String authToken = authenticate();

		// Create calendar
		String calendarJson = """
				{
				  "name": "Zurich",
				  "active": true
				}
				""";
		String calendarId;
		try (Response response = target()
				.path("chronivaro/v1/admin/holiday-calendars")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(calendarJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			JsonObject result = JsonParser.parseString(response.readEntity(String.class)).getAsJsonObject();
			calendarId = result.get("value").getAsString();
		}

		// Create holiday
		String holidayJson = """
				{
				  "name": "New Year",
				  "date": "2025-01-01",
				  "creditFactor": 1.0
				}
				""";
		try (Response response = target()
				.path("chronivaro/v1/admin/holiday-calendars/" + calendarId + "/holidays")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(holidayJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// List calendars
		try (Response response = target()
				.path("chronivaro/v1/admin/holiday-calendars")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			List<HolidayCalendarDto> calendars = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<HolidayCalendarDto>>() {
					}.getType());
			assertFalse(calendars.isEmpty());
		}

		// List holidays
		try (Response response = target()
				.path("chronivaro/v1/admin/holiday-calendars/" + calendarId + "/holidays")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			String json = response.readEntity(String.class);
			List<HolidayDto> holidays = ChronivaroRestHelper
					.createGson()
					.fromJson(json, new TypeToken<List<HolidayDto>>() {
					}.getType());
			assertFalse(holidays.isEmpty());
			assertEquals("New Year", holidays.getFirst().name());
		}

		// Delete calendar
		try (Response response = target()
				.path("chronivaro/v1/admin/holiday-calendars/" + calendarId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.delete()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}
	}

	@Test
	public void shouldNotAllowDuplicateHolidays() {
		String authToken = authenticate();

		// Create calendar
		String calendarJson = """
				{
				  "name": "Zurich",
				  "active": true
				}
				""";
		String calendarId;
		try (Response response = target()
				.path("chronivaro/v1/admin/holiday-calendars")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(calendarJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			JsonObject result = JsonParser.parseString(response.readEntity(String.class)).getAsJsonObject();
			calendarId = result.get("value").getAsString();
		}

		// Create first holiday
		String holiday1Json = """
				{
				  "name": "New Year",
				  "date": "2025-01-01",
				  "creditFactor": 1.0
				}
				""";
		try (Response response = target()
				.path("chronivaro/v1/admin/holiday-calendars/" + calendarId + "/holidays")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(holiday1Json))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Create second holiday on same date
		String holiday2Json = """
				{
				  "name": "Another Holiday",
				  "date": "2025-01-01",
				  "creditFactor": 1.0
				}
				""";
		try (Response response = target()
				.path("chronivaro/v1/admin/holiday-calendars/" + calendarId + "/holidays")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(holiday2Json))) {
			// This should fail with 400 Bad Request or similar
			assertNotEquals("Should not allow duplicate holiday on same date", response.getStatus(),
					Response.Status.OK.getStatusCode());
		}
	}
}
