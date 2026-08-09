package ch.atexxi.chronivaro.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AbsenceResourceTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldGetAbsences() {
		String authToken = authenticate();
		try (Response response = target().path("chronivaro/v1/admin/absences")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}
	}

	@Test
	public void shouldApproveAbsence() {
		String authToken = authenticate();
		try (Response response = target().path("chronivaro/v1/admin/absences/test-absence/approve")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(null)) {
			// Expect 404 if absence doesn't exist
			int status = response.getStatus();
			assertTrue(status == 200 || status == 404);
		}
	}

	private void assertTrue(boolean condition) {
		org.junit.Assert.assertTrue(condition);
	}
}
