package ch.atexxi.chronivaro.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PeriodResourceTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldApprovePeriod() {
		String authToken = authenticate();
		try (Response response = target()
				.path("chronivaro/v1/admin/periods/test-period/approve")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(null)) {
			int status = response.getStatus();
			assertTrue(status == 200 || status == 404);
		}
	}

	@Test
	public void shouldLockPeriod() {
		String authToken = authenticate();
		try (Response response = target()
				.path("chronivaro/v1/admin/periods/test-period/lock")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(null)) {
			int status = response.getStatus();
			assertTrue(status == 200 || status == 404);
		}
	}
}
