package ch.atexxi.chronivaro.rest;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HolidayCalendarResourceTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldCreateHolidayCalendarAndHoliday() {
		String authToken = authenticate();

		// Create calendar
		String calendarJson = """
				{
				  "id": "ch-zh",
				  "name": "Zurich",
				  "active": true
				}
				""";
		try (Response response = target().path("chronivaro/v1/admin/holiday-calendars")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(calendarJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Create holiday
		String holidayJson = """
				{
				  "id": "new-year",
				  "name": "New Year",
				  "date": "2025-01-01",
				  "creditFactor": 1.0
				}
				""";
		try (Response response = target().path("chronivaro/v1/admin/holiday-calendars/ch-zh/holidays")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(holidayJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}
	}
}
