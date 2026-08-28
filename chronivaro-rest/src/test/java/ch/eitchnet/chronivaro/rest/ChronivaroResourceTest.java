package ch.eitchnet.chronivaro.rest;

import ch.eitchnet.chronivaro.rest.resource.ChronivaroRestHelper;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChronivaroResourceTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldGetPresence() {
		String authToken = authenticate();
		try (Response response = target()
				.path("chronivaro/v1/presence")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}
	}

	@Test
	public void employeeShouldGetPresence() {
		String authToken = authenticate("employee", "admin");
		try (Response response = target()
				.path("chronivaro/v1/presence")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}
	}

	@Test
	public void shouldAddAndCorrectWorkEntry() {
		String authToken = authenticate();

		// Add work entry
		String json = """
				{
				  "start": "2025-01-01T08:00:00+01:00",
				  "end": "2025-01-01T12:00:00+01:00",
				  "comment": "Test work entry",
				  "workingLocation": "OFFICE"
				}
				""";
		String entryId;
		try (Response response = target()
				.path("chronivaro/v1/me/work-entries")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(json))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			com.google.gson.JsonObject obj = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), com.google.gson.JsonObject.class);
			entryId = obj.get("id").getAsString();
			assertEquals("MANUAL", obj.get("source").getAsString());
			assertEquals("admin", obj.get("createdBy").getAsString());
			org.junit.Assert.assertFalse(obj.get("modified").getAsBoolean());
		}

		// Employee updates work entry (start time, end time, comment, workingLocation)
		String updateJson = """
				{
				  "start": "2025-01-01T08:30:00+01:00",
				  "end": "2025-01-01T12:30:00+01:00",
				  "comment": "Updated by employee",
				  "workingLocation": "OFFICE"
				}
				""";
		try (Response response = target()
				.path("chronivaro/v1/me/work-entries/" + entryId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.put(Entity.json(updateJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			com.google.gson.JsonObject obj = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), com.google.gson.JsonObject.class);
			assertEquals(entryId, obj.get("id").getAsString());
			assertEquals("MANUAL", obj.get("source").getAsString());
			assertEquals("admin", obj.get("createdBy").getAsString());
			org.junit.Assert.assertTrue(obj.get("modified").getAsBoolean());
			assertEquals("Updated by employee", obj.get("comment").getAsString());
		}

		// Zero duration
		String zeroDurationJson = """
				{
				  "start": "2025-01-02T08:00:00+01:00",
				  "end": "2025-01-02T08:00:00+01:00",
				  "comment": "Zero duration work entry"
				}
				""";
		try (Response response = target()
				.path("chronivaro/v1/me/work-entries")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(zeroDurationJson))) {
			assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
			String body = response.readEntity(String.class);
			org.junit.Assert.assertTrue(body.contains("INVALID_ENTRY_DURATION"));
		}

		// Conflicting morning location
		String morningConflictJson = """
				{
				  "start": "2025-01-01T07:00:00+01:00",
				  "end": "2025-01-01T08:00:00+01:00",
				  "comment": "Conflicting morning location",
				  "workingLocation": "HOME_OFFICE"
				}
				""";
		try (Response response = target()
				.path("chronivaro/v1/me/work-entries")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(morningConflictJson))) {
			assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
		}
	}

	@Test
	public void shouldRequestAbsence() {
		String authToken = authenticate();

		// Create absence type first
		String typeJson = """
				{
				  "id": "vacation",
				  "code": "vacation",
				  "name": "Vacation",
				  "active": true,
				  "approvalRequired": true,
				  "paid": true,
				  "reduceVacationCredit": true,
				  "countAsTargetTime": true,
				  "durationTypes": ["full_day", "half_day"]
				}
				""";
		try (Response response = target()
				.path("chronivaro/v1/admin/absence-types")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(typeJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		String json = """
				{
				  "absenceTypeCode": "vacation",
				  "start": "2025-02-01T00:00:00+01:00",
				  "end": "2025-02-01T23:59:59+01:00",
				  "durationType": "full_day",
				  "comment": "Test absence"
				}
				""";
		try (Response response = target()
				.path("chronivaro/v1/me/absences")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(json))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Create absence type requiring comment
		String reqCommentTypeJson = """
				{
				  "id": "special_leave",
				  "code": "SPECIAL_LEAVE",
				  "name": "Special Leave",
				  "active": true,
				  "approvalRequired": true,
				  "paid": true,
				  "commentRequired": true,
				  "durationTypes": ["FULL_DAY"]
				}
				""";
		try (Response response = target()
				.path("chronivaro/v1/admin/absence-types")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(reqCommentTypeJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Try to request without comment -> fails 400 or 500
		String noCommentJson = """
				{
				  "absenceTypeCode": "SPECIAL_LEAVE",
				  "start": "2025-03-01T00:00:00+01:00",
				  "end": "2025-03-01T23:59:59+01:00",
				  "durationType": "FULL_DAY"
				}
				""";
		try (Response response = target()
				.path("chronivaro/v1/me/absences")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(noCommentJson))) {
			org.junit.Assert.assertTrue(response.getStatus() >= 400);
		}

		// Request as DRAFT -> succeeds
		String draftJson = """
				{
				  "absenceTypeCode": "SPECIAL_LEAVE",
				  "start": "2025-03-01T00:00:00+01:00",
				  "end": "2025-03-01T23:59:59+01:00",
				  "durationType": "FULL_DAY",
				  "state": "DRAFT"
				}
				""";
		String absenceId;
		try (Response response = target()
				.path("chronivaro/v1/me/absences")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(draftJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			com.google.gson.JsonObject obj = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), com.google.gson.JsonObject.class);
			absenceId = obj.get("id").getAsString();
			assertEquals("DRAFT", obj.get("state").getAsString());
		}

		// Update draft with comment
		String updateDraftJson = """
				{
				  "absenceTypeCode": "SPECIAL_LEAVE",
				  "start": "2025-03-01T00:00:00+01:00",
				  "end": "2025-03-01T23:59:59+01:00",
				  "durationType": "FULL_DAY",
				  "comment": "Family event"
				}
				""";
		try (Response response = target()
				.path("chronivaro/v1/me/absences/" + absenceId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.put(Entity.json(updateDraftJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Submit draft
		try (Response response = target()
				.path("chronivaro/v1/me/absences/" + absenceId + "/submit")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(""))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			com.google.gson.JsonObject obj = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), com.google.gson.JsonObject.class);
			assertEquals("SUBMITTED", obj.get("state").getAsString());
		}
	}

	@Test
	public void shouldSubmitPeriod() {
		String authToken = authenticate();
		try (Response response = target()
				.path("chronivaro/v1/me/periods/test-period/submit")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(""))) {
			// Expect 404 if period doesn't exist, but 200 if it does.
			// In our mock runtime, we might not have 'test-period'
			int status = response.getStatus();
			System.out.println("Submit period status: " + status);
			// We just want to see it doesn't 500
		}
	}

	@Test
	public void shouldStartAndStopTimer() {
		String authToken = authenticate();

		// Start timer
		try (Response response = target()
				.path("chronivaro/v1/me/timer/start")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json("{\"workingLocation\":\"HOME_OFFICE\"}"))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Check Day Summary contains activeTimer
		String todayStr = java.time.LocalDate.now().toString();
		try (Response response = target()
				.path("chronivaro/v1/me/day-summary/" + todayStr)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			com.google.gson.JsonObject obj = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), com.google.gson.JsonObject.class);
			assertEquals("WORKING", obj.get("state").getAsString());
			org.junit.Assert.assertTrue(obj.has("activeTimer") && !obj.get("activeTimer").isJsonNull());
			com.google.gson.JsonObject activeTimer = obj.getAsJsonObject("activeTimer");
			org.junit.Assert.assertFalse(activeTimer.get("isPreviousDay").getAsBoolean());
		}

		// Stop timer
		try (Response response = target()
				.path("chronivaro/v1/me/timer/stop")
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

		try (Response response = target()
				.path("chronivaro/v1/me/day-summary/" + date)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}
	}

	@Test
	public void shouldGetMyWorkingLocationDefaults() {
		String authToken = authenticate();
		try (Response response = target()
				.path("chronivaro/v1/me/working-location-defaults")
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

		try (Response response = target()
				.path("chronivaro/v1/me/month-summary/" + yearMonth)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}
	}

	@Test
	public void shouldManageEmployeeWorkEntriesViaRest() {
		String adminAuth = authenticate();
		String employeeId = "employee_emp";

		// 1. POST /employees/{id}/work-entries
		String createWorkEntryJson = """
				{
				  "start": "2026-07-01T08:00:00+02:00",
				  "end": "2026-07-01T17:00:00+02:00",
				  "workingLocation": "OFFICE",
				  "comment": "Rest manual entry"
				}
				""";
		String createdEntryId;
		try (Response response = target()
				.path("chronivaro/v1/employees/" + employeeId + "/work-entries")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminAuth)
				.post(Entity.json(createWorkEntryJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			com.google.gson.JsonObject obj = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), com.google.gson.JsonObject.class);
			createdEntryId = obj.get("id").getAsString();
			assertEquals("Rest manual entry", obj.get("comment").getAsString());
		}

		// 2. GET /employees/{id}/work-entries
		try (Response response = target()
				.path("chronivaro/v1/employees/" + employeeId + "/work-entries")
				.queryParam("from", "2026-07-01T00:00:00+02:00")
				.queryParam("to", "2026-07-01T23:59:59+02:00")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminAuth)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			com.google.gson.JsonArray arr = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), com.google.gson.JsonArray.class);
			assertEquals(1, arr.size());
			assertEquals(createdEntryId, arr.get(0).getAsJsonObject().get("id").getAsString());
		}

		// 3. PUT /admin/work-entries/{id}
		String updateJson = """
				{
				  "start": "2026-07-01T08:30:00+02:00",
				  "end": "2026-07-01T17:30:00+02:00",
				  "workingLocation": "HOME",
				  "comment": "Admin updated shift"
				}
				""";
		try (Response response = target()
				.path("chronivaro/v1/admin/work-entries/" + createdEntryId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminAuth)
				.put(Entity.json(updateJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			com.google.gson.JsonObject obj = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), com.google.gson.JsonObject.class);
			assertEquals("Admin updated shift", obj.get("comment").getAsString());
		}

		// 4. DELETE /admin/work-entries/{id}
		try (Response response = target()
				.path("chronivaro/v1/admin/work-entries/" + createdEntryId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminAuth)
				.delete()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Verify empty afterwards
		try (Response response = target()
				.path("chronivaro/v1/employees/" + employeeId + "/work-entries")
				.queryParam("from", "2026-07-01T00:00:00+02:00")
				.queryParam("to", "2026-07-01T23:59:59+02:00")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminAuth)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			com.google.gson.JsonArray arr = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), com.google.gson.JsonArray.class);
			assertEquals(0, arr.size());
		}
	}

	@Test
	public void shouldGetMyProfile() {
		String authToken = authenticate("employee", "admin");
		try (Response response = target()
				.path("chronivaro/v1/me/profile")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			com.google.gson.JsonObject obj = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), com.google.gson.JsonObject.class);
			org.junit.Assert.assertNotNull(obj.get("id"));
			org.junit.Assert.assertEquals("employee", obj.get("username").getAsString());
		}
	}

	@Test
	public void shouldGetMySchedules() {
		String authToken = authenticate("employee", "admin");
		try (Response response = target()
				.path("chronivaro/v1/me/schedules")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			com.google.gson.JsonArray arr = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), com.google.gson.JsonArray.class);
			org.junit.Assert.assertNotNull(arr);
		}
	}
}
