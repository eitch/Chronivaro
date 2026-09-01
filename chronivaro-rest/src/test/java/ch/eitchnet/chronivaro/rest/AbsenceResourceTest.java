package ch.eitchnet.chronivaro.rest;

import ch.eitchnet.chronivaro.rest.dto.AbsenceDto;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AbsenceResourceTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldGetAbsences() {
		String authToken = authenticate();
		try (Response response = target()
				.path("chronivaro/v1/admin/absences")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}
	}

	@Test
	public void shouldCreateAndGetEmployeeAbsences() {
		String authToken = authenticate();
		String employeeId = "employee_emp";

		// 1. Create Absence for Employee as Admin (direct approved)
		JsonObject payload = new JsonObject();
		payload.addProperty("absenceTypeCode", "VACATION");
		payload.addProperty("start", "2026-08-10T00:00:00.000+02:00");
		payload.addProperty("end", "2026-08-10T23:59:59.999+02:00");
		payload.addProperty("durationType", "full_day");
		payload.addProperty("state", "APPROVED");
		payload.addProperty("comment", "Manager booked vacation");

		String createdId;
		try (Response response = target()
				.path("chronivaro/v1/employees/" + employeeId + "/absences")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(payload.toString()))) {
			String json = response.readEntity(String.class);
			assertEquals("Response error: " + json, Response.Status.OK.getStatusCode(), response.getStatus());
			JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
			createdId = obj.get("id").getAsString();
			assertEquals("APPROVED", obj.get("state").getAsString());
			assertEquals("admin", obj.get("createdBy").getAsString());
		}

		// 2. Fetch Absences for Employee
		try (Response response = target()
				.path("chronivaro/v1/employees/" + employeeId + "/absences")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			String json = response.readEntity(String.class);
			assertTrue(JsonParser.parseString(json).isJsonArray());
		}
	}

	@Test
	public void shouldApproveAbsence() {
		String authToken = authenticate();
		try (Response response = target()
				.path("chronivaro/v1/admin/absences/test-absence/approve")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(null)) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}
	}
}
