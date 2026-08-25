package ch.eitchnet.chronivaro.rest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PeriodResourceTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldGetMyPeriodStatusAndAutoCreateOpen() {
		String employeeToken = authenticate("employee", "admin");
		try (Response response = target()
				.path("chronivaro/v1/me/periods/2025-05")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(200, response.getStatus());

			JsonObject json = JsonParser.parseString(response.readEntity(String.class)).getAsJsonObject();
			assertEquals("employee_emp", json.get("employeeId").getAsString());
			assertEquals("2025-05", json.get("yearMonth").getAsString());
			assertEquals("OPEN", json.get("status").getAsString());
		}
	}

	@Test
	public void shouldSubmitMyPeriodAndTransitionToSubmitted() {
		String employeeToken = authenticate("employee", "admin");

		JsonObject submitReq = new JsonObject();
		submitReq.addProperty("comment", "Submitting for approval");

		try (Response response = target()
				.path("chronivaro/v1/me/periods/2025-06/submit")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.post(Entity.entity(submitReq.toString(), MediaType.APPLICATION_JSON))) {

			assertEquals(200, response.getStatus());
			assertNotNull(response.getHeaderString("ETag"));

			JsonObject json = JsonParser.parseString(response.readEntity(String.class)).getAsJsonObject();
			assertEquals("employee_emp", json.get("employeeId").getAsString());
			assertEquals("2025-06", json.get("yearMonth").getAsString());
			assertEquals("SUBMITTED", json.get("status").getAsString());
			assertNotNull(json.get("submittedAt"));
		}
	}

	@Test
	public void shouldQueryPeriodStatus() {
		String employeeToken = authenticate("employee", "admin");
		String adminToken = authenticate("admin", "admin");

		// Submit period 2025-01 first
		JsonObject submitReq = new JsonObject();
		submitReq.addProperty("comment", "Jan submit");
		try (Response response = target()
				.path("chronivaro/v1/me/periods/2025-01/submit")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.post(Entity.entity(submitReq.toString(), MediaType.APPLICATION_JSON))) {
			assertEquals(200, response.getStatus());
		}

		try (Response response = target()
				.path("chronivaro/v1/periods/status")
				.queryParam("yearMonth", "2025-01")
				.queryParam("employeeId", "employee_emp")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.get()) {

			assertEquals(200, response.getStatus());
			JsonObject json = JsonParser.parseString(response.readEntity(String.class)).getAsJsonObject();
			assertEquals("employee_emp", json.get("employeeId").getAsString());
			assertEquals("2025-01", json.get("yearMonth").getAsString());
			assertEquals("SUBMITTED", json.get("status").getAsString());
		}
	}

	@Test
	public void shouldRejectPeriodWithMandatoryReason() {
		String employeeToken = authenticate("employee", "admin");
		String supervisorToken = authenticate("supervisor", "admin");

		// Submit period 2025-07 first
		JsonObject submitReq = new JsonObject();
		submitReq.addProperty("comment", "July submit");
		try (Response response = target()
				.path("chronivaro/v1/me/periods/2025-07/submit")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.post(Entity.entity(submitReq.toString(), MediaType.APPLICATION_JSON))) {
			assertEquals(200, response.getStatus());
		}

		// Reject without comment should fail with 400 Bad Request
		JsonObject invalidReq = new JsonObject();
		invalidReq.addProperty("employeeId", "employee_emp");
		invalidReq.addProperty("yearMonth", "2025-07");

		try (Response response = target()
				.path("chronivaro/v1/periods/reject")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", supervisorToken)
				.post(Entity.entity(invalidReq.toString(), MediaType.APPLICATION_JSON))) {

			assertEquals(400, response.getStatus());
		}

		// Reject with comment should succeed
		JsonObject validReq = new JsonObject();
		validReq.addProperty("employeeId", "employee_emp");
		validReq.addProperty("yearMonth", "2025-07");
		validReq.addProperty("comment", "Please clarify missing times on 2025-07-15");

		try (Response response = target()
				.path("chronivaro/v1/periods/reject")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", supervisorToken)
				.post(Entity.entity(validReq.toString(), MediaType.APPLICATION_JSON))) {

			assertEquals(200, response.getStatus());
			JsonObject json = JsonParser.parseString(response.readEntity(String.class)).getAsJsonObject();
			assertEquals("REJECTED", json.get("status").getAsString());
		}
	}

	@Test
	public void shouldReopenPeriodWithMandatoryReason() {
		String employeeToken = authenticate("employee", "admin");
		String supervisorToken = authenticate("supervisor", "admin");

		// Submit period 2025-08 first
		JsonObject submitReq = new JsonObject();
		submitReq.addProperty("comment", "Aug submit");
		try (Response response = target()
				.path("chronivaro/v1/me/periods/2025-08/submit")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.post(Entity.entity(submitReq.toString(), MediaType.APPLICATION_JSON))) {
			assertEquals(200, response.getStatus());
		}

		// Reopen without comment should fail with 400 Bad Request
		JsonObject invalidReq = new JsonObject();
		invalidReq.addProperty("employeeId", "employee_emp");
		invalidReq.addProperty("yearMonth", "2025-08");

		try (Response response = target()
				.path("chronivaro/v1/periods/reopen")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", supervisorToken)
				.post(Entity.entity(invalidReq.toString(), MediaType.APPLICATION_JSON))) {

			assertEquals(400, response.getStatus());
		}

		// Reopen with comment should succeed and transition back to OPEN
		JsonObject validReq = new JsonObject();
		validReq.addProperty("employeeId", "employee_emp");
		validReq.addProperty("yearMonth", "2025-08");
		validReq.addProperty("comment", "Reopened for employee time corrections");

		try (Response response = target()
				.path("chronivaro/v1/periods/reopen")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", supervisorToken)
				.post(Entity.entity(validReq.toString(), MediaType.APPLICATION_JSON))) {

			assertEquals(200, response.getStatus());
			JsonObject json = JsonParser.parseString(response.readEntity(String.class)).getAsJsonObject();
			assertEquals("OPEN", json.get("status").getAsString());
		}
	}

	@Test
	public void shouldResubmitAndApproveViaApprovalsQueue() {
		String employeeToken = authenticate("employee", "admin");
		String supervisorToken = authenticate("supervisor", "admin");

		// Employee submits
		JsonObject submitReq = new JsonObject();
		submitReq.addProperty("comment", "Submitting Sept for approval");
		try (Response response = target()
				.path("chronivaro/v1/me/periods/2025-09/submit")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.post(Entity.entity(submitReq.toString(), MediaType.APPLICATION_JSON))) {
			assertEquals(200, response.getStatus());
		}

		// Supervisor checks approvals queue
		try (Response response = target()
				.path("chronivaro/v1/approvals/periods")
				.queryParam("offset", 0)
				.queryParam("limit", 10)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", supervisorToken)
				.get()) {

			assertEquals(200, response.getStatus());
			JsonObject json = JsonParser.parseString(response.readEntity(String.class)).getAsJsonObject();
			assertTrue(json.has("data"));
			assertTrue(json.get("data").getAsJsonArray().size() > 0);
		}

		// Supervisor approves via approvals queue endpoint
		JsonObject approveReq = new JsonObject();
		approveReq.addProperty("comment", "Looks good now");
		try (Response response = target()
				.path("chronivaro/v1/approvals/periods/period-employee_emp-2025-09/approve")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", supervisorToken)
				.post(Entity.entity(approveReq.toString(), MediaType.APPLICATION_JSON))) {

			assertEquals(200, response.getStatus());
			JsonObject json = JsonParser.parseString(response.readEntity(String.class)).getAsJsonObject();
			assertEquals("APPROVED", json.get("status").getAsString());
			assertEquals("supervisor", json.get("approvedBy").getAsString());
			assertNotNull(json.get("approvedAt"));
		}
	}

	@Test
	public void shouldLockPeriodByHR() {
		String employeeToken = authenticate("employee", "admin");
		String supervisorToken = authenticate("supervisor", "admin");
		String hrToken = authenticate("hr", "admin");

		// Submit period 2025-10
		JsonObject submitReq = new JsonObject();
		submitReq.addProperty("comment", "Oct submit");
		try (Response response = target()
				.path("chronivaro/v1/me/periods/2025-10/submit")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.post(Entity.entity(submitReq.toString(), MediaType.APPLICATION_JSON))) {
			assertEquals(200, response.getStatus());
		}

		// Approve period 2025-10
		JsonObject approveReq = new JsonObject();
		approveReq.addProperty("employeeId", "employee_emp");
		approveReq.addProperty("yearMonth", "2025-10");
		try (Response response = target()
				.path("chronivaro/v1/periods/approve")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", supervisorToken)
				.post(Entity.entity(approveReq.toString(), MediaType.APPLICATION_JSON))) {
			assertEquals(200, response.getStatus());
		}

		// HR locks period
		try (Response response = target()
				.path("chronivaro/v1/admin/periods/period-employee_emp-2025-10/lock")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", hrToken)
				.post(Entity.entity("{}", MediaType.APPLICATION_JSON))) {

			assertEquals(200, response.getStatus());
			JsonObject json = JsonParser.parseString(response.readEntity(String.class)).getAsJsonObject();
			assertEquals("LOCKED", json.get("status").getAsString());
		}
	}

	@Test
	public void shouldEnforceRolePrivileges() {
		String employeeToken = authenticate("employee", "admin");

		// Employee cannot approve period
		JsonObject approveReq = new JsonObject();
		approveReq.addProperty("employeeId", "employee_emp");
		approveReq.addProperty("yearMonth", "2025-06");

		try (Response response = target()
				.path("chronivaro/v1/periods/approve")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.post(Entity.entity(approveReq.toString(), MediaType.APPLICATION_JSON))) {

			assertEquals(403, response.getStatus());
		}

		// Employee cannot lock period
		try (Response response = target()
				.path("chronivaro/v1/admin/periods/test-period-lock/lock")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.post(Entity.entity("{}", MediaType.APPLICATION_JSON))) {

			assertEquals(403, response.getStatus());
		}
	}

	@Test
	public void shouldEnforceConcurrencyControlWithIfMatch() {
		String employeeToken = authenticate("employee", "admin");
		String supervisorToken = authenticate("supervisor", "admin");

		// Submit period 2025-03
		JsonObject submitReq = new JsonObject();
		submitReq.addProperty("comment", "March submit");
		try (Response response = target()
				.path("chronivaro/v1/me/periods/2025-03/submit")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.post(Entity.entity(submitReq.toString(), MediaType.APPLICATION_JSON))) {
			assertEquals(200, response.getStatus());
		}

		// Attempting reopen with a stale/mismatched If-Match header should yield 409 Conflict
		JsonObject reopenReq = new JsonObject();
		reopenReq.addProperty("comment", "Reopening attempt");

		try (Response response = target()
				.path("chronivaro/v1/periods/period-employee_emp-2025-03/reopen")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", supervisorToken)
				.header("If-Match", "\"999999\"")
				.post(Entity.entity(reopenReq.toString(), MediaType.APPLICATION_JSON))) {

			assertEquals(409, response.getStatus());
		}
	}

	@Test
	public void shouldRequireAuthenticationAndValidateParameters() {
		// Unauthenticated
		try (Response response = target()
				.path("chronivaro/v1/me/periods/2025-05")
				.request(MediaType.APPLICATION_JSON)
				.get()) {

			assertEquals(401, response.getStatus());
		}

		// Invalid yearMonth format
		String employeeToken = authenticate("employee", "admin");
		try (Response response = target()
				.path("chronivaro/v1/me/periods/invalid-month")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(400, response.getStatus());
		}
	}

	@Test
	public void shouldApproveAdminPeriod() {
		String authToken = authenticate();
		try (Response response = target()
				.path("chronivaro/v1/admin/periods/test-period-approve/approve")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.entity("{}", MediaType.APPLICATION_JSON))) {
			assertEquals(200, response.getStatus());
		}
	}

	@Test
	public void shouldLockAdminPeriod() {
		String authToken = authenticate();
		try (Response response = target()
				.path("chronivaro/v1/admin/periods/test-period-lock/lock")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.entity("{}", MediaType.APPLICATION_JSON))) {
			assertEquals(200, response.getStatus());
		}
	}
}
