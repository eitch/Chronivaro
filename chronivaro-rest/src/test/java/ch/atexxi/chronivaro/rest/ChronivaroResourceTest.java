package ch.atexxi.chronivaro.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChronivaroResourceTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldStartAndStopTimer() {
		String authToken = authenticate();

		// Start timer
		try (Response response = target().path("chronivaro/v1/me/timer/start")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(null)) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Stop timer
		try (Response response = target().path("chronivaro/v1/me/timer/stop")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(null)) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}
	}

	@Test
	public void shouldGetDaySummary() {
		String authToken = authenticate();
		String date = "2025-01-01";

		try (Response response = target().path("chronivaro/v1/me/day-summary/" + date)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}
	}

	@Test
	public void shouldGetMonthSummary() {
		String authToken = authenticate();
		String yearMonth = "2025-01";

		try (Response response = target().path("chronivaro/v1/me/month-summary/" + yearMonth)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}
	}
}
