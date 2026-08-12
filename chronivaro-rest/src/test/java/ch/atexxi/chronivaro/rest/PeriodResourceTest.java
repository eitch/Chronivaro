package ch.atexxi.chronivaro.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PeriodResourceTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldApprovePeriod() {
		String authToken = authenticate();
		try (Response response = target()
				.path("chronivaro/v1/admin/periods/test-period-approve/approve")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(null)) {
			assertEquals(200, response.getStatus());
		}
	}

	@Test
	public void shouldLockPeriod() {
		String authToken = authenticate();
		try (Response response = target()
				.path("chronivaro/v1/admin/periods/test-period-lock/lock")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(null)) {
			assertEquals(200, response.getStatus());
		}
	}
}
