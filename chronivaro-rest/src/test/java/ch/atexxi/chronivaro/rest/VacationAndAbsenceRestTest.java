package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.rest.dto.*;
import ch.atexxi.chronivaro.rest.resource.ChronivaroRestHelper;
import com.google.gson.reflect.TypeToken;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class VacationAndAbsenceRestTest extends AbstractChronivaroRestfulTest {

	private static final String ZONE = "Europe/Zurich";

	@Before
	public void setupTestData() {
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(runtimeMock.loginAdmin())) {
			tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY).forEach(tx::remove);
			tx.streamResources(TYPE_ABSENCE).forEach(tx::remove);
			tx.streamResources(TYPE_EMPLOYMENT_SCHEDULE).forEach(tx::remove);
			tx.streamResources(TYPE_EMPLOYEE).forEach(tx::remove);

			// Location
			Resource loc = tx.getResourceBy(TYPE_LOCATION, "test-loc");
			if (loc == null) {
				loc = tx.getResourceTemplate(TYPE_LOCATION, true);
				loc.setId("test-loc");
				loc.setName("Test Location");
				loc.setString(PARAM_TIMEZONE, ZONE);
				tx.add(loc);
			}

			// Team
			Resource team = tx.getResourceBy(TYPE_TEAM, "test-team");
			if (team == null) {
				team = tx.getResourceTemplate(TYPE_TEAM, true);
				team.setId("test-team");
				team.setName("Test Team");
				tx.add(team);
			}

			// Absence Type Vacation
			Resource vacationType = tx.getResourceBy(TYPE_ABSENCE_TYPE, "vacation");
			if (vacationType == null) {
				vacationType = tx.getResourceTemplate(TYPE_ABSENCE_TYPE, true);
				vacationType.setId("vacation");
				vacationType.setName("Vacation");
				vacationType.setString(PARAM_CODE, "VACATION");
				vacationType.setBoolean(PARAM_REDUCE_VACATION_CREDIT, true);
				vacationType.setBoolean(PARAM_PAID, true);
				vacationType.setBoolean(PARAM_APPROVAL_REQUIRED, true);
				vacationType.setBoolean(PARAM_ACTIVE, true);
				tx.add(vacationType);
			} else {
				vacationType.setString(PARAM_CODE, "VACATION");
				vacationType.setBoolean(PARAM_REDUCE_VACATION_CREDIT, true);
				vacationType.setBoolean(PARAM_PAID, true);
				vacationType.setBoolean(PARAM_APPROVAL_REQUIRED, true);
				vacationType.setBoolean(PARAM_ACTIVE, true);
				tx.update(vacationType);
			}

			// Absence Type Sick
			Resource sickType = tx.getResourceBy(TYPE_ABSENCE_TYPE, "sick");
			if (sickType == null) {
				sickType = tx.getResourceTemplate(TYPE_ABSENCE_TYPE, true);
				sickType.setId("sick");
				sickType.setName("Sick");
				sickType.setString(PARAM_CODE, "SICK");
				sickType.setBoolean(PARAM_REDUCE_VACATION_CREDIT, false);
				sickType.setBoolean(PARAM_PAID, true);
				sickType.setBoolean(PARAM_APPROVAL_REQUIRED, false);
				sickType.setBoolean(PARAM_ACTIVE, true);
				tx.add(sickType);
			} else {
				sickType.setString(PARAM_CODE, "SICK");
				sickType.setBoolean(PARAM_REDUCE_VACATION_CREDIT, false);
				sickType.setBoolean(PARAM_PAID, true);
				sickType.setBoolean(PARAM_APPROVAL_REQUIRED, false);
				sickType.setBoolean(PARAM_ACTIVE, true);
				tx.update(sickType);
			}

			// Employee 1 (linked to admin for /me endpoints)
			Resource emp1 = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			emp1.setId("emp-admin");
			emp1.setName("Admin User");
			emp1.setString(PARAM_FIRSTNAME, "Admin");
			emp1.setString(PARAM_LASTNAME, "User");
			emp1.setString(PARAM_TIMEZONE, ZONE);
			emp1.setDate(PARAM_JOIN_DATE, LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of(ZONE)));
			emp1.setBoolean(PARAM_ACTIVE, true);
			emp1.setString(PARAM_USERNAME, "admin");
			emp1.setString(PARAM_USER_ID, "admin");
			emp1.setRelation(PARAM_LOCATION, loc);
			emp1.setRelation(PARAM_PRIMARY_TEAM, team);

			Resource sched1 = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			sched1.setId("sched-emp-admin");
			sched1.setName("Schedule Admin");
			sched1.setRelation(PARAM_EMPLOYEE, emp1);
			sched1.setDate(PARAM_VALID_FROM, LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of(ZONE)));
			sched1.setDouble(PARAM_EMPLOYMENT_RATE, 1.0);
			sched1.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, 480);
			sched1.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, 480);
			sched1.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, 480);
			sched1.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, 480);
			sched1.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, 480);
			sched1.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, 0);
			sched1.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, 0);
			sched1.setInteger(PARAM_WEEKLY_TARGET_MINUTES, 2400);
			tx.add(sched1);
			emp1.setRelation(PARAM_CURRENT_SCHEDULE, sched1);
			tx.add(emp1);

			// Employee 2 (employee user from PrivilegeUsers.xml)
			Resource emp2 = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			emp2.setId("emp-test");
			emp2.setName("Employee User");
			emp2.setString(PARAM_FIRSTNAME, "Employee");
			emp2.setString(PARAM_LASTNAME, "User");
			emp2.setString(PARAM_TIMEZONE, ZONE);
			emp2.setDate(PARAM_JOIN_DATE, LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of(ZONE)));
			emp2.setBoolean(PARAM_ACTIVE, true);
			emp2.setString(PARAM_USERNAME, "employee");
			emp2.setString(PARAM_USER_ID, "employee");
			emp2.setRelation(PARAM_LOCATION, loc);
			emp2.setRelation(PARAM_PRIMARY_TEAM, team);

			Resource sched2 = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			sched2.setId("sched-emp-test");
			sched2.setName("Schedule Test");
			sched2.setRelation(PARAM_EMPLOYEE, emp2);
			sched2.setDate(PARAM_VALID_FROM, LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of(ZONE)));
			sched2.setDouble(PARAM_EMPLOYMENT_RATE, 1.0);
			sched2.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, 480);
			sched2.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, 480);
			sched2.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, 480);
			sched2.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, 480);
			sched2.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, 480);
			sched2.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, 0);
			sched2.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, 0);
			sched2.setInteger(PARAM_WEEKLY_TARGET_MINUTES, 2400);
			tx.add(sched2);
			emp2.setRelation(PARAM_CURRENT_SCHEDULE, sched2);
			tx.add(emp2);

			tx.commitOnClose();
		}
	}

	@Test
	public void testPersonalVacationAccountEndpoint() {
		String adminToken = authenticate("admin", "admin");

		// 1. Credit vacation entitlement for 2025 (25 days = 12000 minutes)
		try (Response response = target()
				.path("chronivaro/v1/admin/employees/emp-admin/vacation-entitlement/credit")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, adminToken)
				.post(Entity.json("{\"year\":2025}"))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			VacationEntitlementCreditDto creditDto = ChronivaroRestHelper.createGson().fromJson(
					response.readEntity(String.class), VacationEntitlementCreditDto.class);
			assertEquals(12000, creditDto.entitlementMinutes());
			assertEquals(2025, creditDto.year());
		}

		// 2. Add a vacation correction (+480 min)
		try (Response response = target()
				.path("chronivaro/v1/admin/employees/emp-admin/vacation-corrections")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, adminToken)
				.post(Entity.json("{\"value\":480,\"comment\":\"Bonus Day\",\"date\":\"2025-06-01T00:00:00+02:00\"}"))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// 3. Query personal vacation account via GET /chronivaro/v1/me/vacation-account
		try (Response response = target()
				.path("chronivaro/v1/me/vacation-account")
				.queryParam("year", 2025)
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, adminToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			VacationAccountSummaryDto summary = ChronivaroRestHelper.createGson().fromJson(
					response.readEntity(String.class), VacationAccountSummaryDto.class);
			assertEquals("emp-admin", summary.employeeId());
			assertEquals(2025, summary.year());
			assertEquals(12000, summary.entitlementMinutes());
			assertEquals(480, summary.correctionsMinutes());
			assertEquals(0, summary.usageMinutes());
			assertEquals(12480, summary.remainingMinutes());
			assertEquals(2, summary.entries().size());
		}

		// 4. Default query without year parameter should also succeed
		try (Response response = target()
				.path("chronivaro/v1/me/vacation-account")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, adminToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}
	}

	@Test
	public void testAdminVacationEndpointsAndPagination() {
		String adminToken = authenticate("admin", "admin");

		// Credit 2025 entitlement
		try (Response response = target()
				.path("chronivaro/v1/admin/employees/emp-test/vacation-entitlement/credit")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, adminToken)
				.post(Entity.json("{\"year\":2025}"))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Add multiple corrections
		for (int i = 1; i <= 3; i++) {
			try (Response response = target()
					.path("chronivaro/v1/admin/employees/emp-test/vacation-corrections")
					.request(MediaType.APPLICATION_JSON)
					.header(HttpHeaders.AUTHORIZATION, adminToken)
					.post(Entity.json("{\"value\":" + (i * 60) + ",\"comment\":\"Correction " + i + "\",\"date\":\"2025-06-01T00:00:00+02:00\"}"))) {
				assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			}
		}

		// Query with summary=true
		try (Response response = target()
				.path("chronivaro/v1/admin/employees/emp-test/vacation-account")
				.queryParam("year", 2025)
				.queryParam("summary", true)
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, adminToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			VacationAccountSummaryDto summary = ChronivaroRestHelper.createGson().fromJson(
					response.readEntity(String.class), VacationAccountSummaryDto.class);
			assertEquals(12000, summary.entitlementMinutes());
			assertEquals(360, summary.correctionsMinutes());
			assertEquals(4, summary.entries().size());
		}

		// Query with pagination (offset=1, limit=2)
		try (Response response = target()
				.path("chronivaro/v1/admin/employees/emp-test/vacation-account")
				.queryParam("year", 2025)
				.queryParam("offset", 1)
				.queryParam("limit", 2)
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, adminToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			Type type = new TypeToken<PagedResultDto<VacationAccountEntryDto>>() {}.getType();
			PagedResultDto<VacationAccountEntryDto> paged = ChronivaroRestHelper.createGson().fromJson(
					response.readEntity(String.class), type);
			assertEquals(1, paged.offset());
			assertEquals(2, paged.limit());
			assertEquals(4, paged.total());
			assertEquals(2, paged.size());
			assertEquals(2, paged.data().size());
		}

		// Calculate vacation entitlement
		try (Response response = target()
				.path("chronivaro/v1/admin/employees/emp-test/vacation-entitlement/calculate")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, adminToken)
				.post(Entity.json("{\"year\":2025}"))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			VacationEntitlementCalculationDto calc = ChronivaroRestHelper.createGson().fromJson(
					response.readEntity(String.class), VacationEntitlementCalculationDto.class);
			assertEquals(12000, calc.entitlementMinutes());
			assertNotNull(calc.summary());
		}
	}

	@Test
	public void testPersonalAbsenceEndpointsFiltersAndCrossUserSecurity() {
		String adminToken = authenticate("admin", "admin");
		String testToken = authenticate("employee", "admin");

		// 1. Admin creates absence 1 (Vacation in Feb 2025)
		String abs1Json = """
				{
				  "absenceTypeCode": "VACATION",
				  "start": "2025-02-10T00:00:00+01:00",
				  "end": "2025-02-14T23:59:59+01:00",
				  "durationType": "full_day",
				  "comment": "Admin Ski Vacation"
				}
				""";
		String abs1Id;
		String abs1Etag;
		try (Response response = target()
				.path("chronivaro/v1/me/absences")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, adminToken)
				.post(Entity.json(abs1Json))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			abs1Etag = response.getHeaderString(HttpHeaders.ETAG);
			assertNotNull(abs1Etag);
			AbsenceDto dto = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), AbsenceDto.class);
			abs1Id = dto.id();
			assertNotNull(abs1Id);
			assertEquals("VACATION", dto.absenceTypeCode());
		}

		// 2. Admin creates absence 2 (Sick in March 2025)
		String abs2Json = """
				{
				  "absenceTypeCode": "SICK",
				  "start": "2025-03-03T00:00:00+01:00",
				  "end": "2025-03-03T23:59:59+01:00",
				  "durationType": "full_day",
				  "comment": "Doctor appointment"
				}
				""";
		try (Response response = target()
				.path("chronivaro/v1/me/absences")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, adminToken)
				.post(Entity.json(abs2Json))) {
			String body = response.readEntity(String.class);
			assertEquals("Failed to create abs2: " + body, Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// 3. Filter GET /me/absences by date range (from 2025-02-01 to 2025-02-28) -> should return 1 absence
		try (Response response = target()
				.path("chronivaro/v1/me/absences")
				.queryParam("from", "2025-02-01")
				.queryParam("to", "2025-02-28")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, adminToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			Type listType = new TypeToken<List<AbsenceDto>>() {}.getType();
			List<AbsenceDto> list = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), listType);
			assertEquals(1, list.size());
			assertEquals("VACATION", list.get(0).absenceTypeCode());
		}

		// 4. Filter GET /me/absences by absenceTypeCode=SICK -> should return 1 absence
		try (Response response = target()
				.path("chronivaro/v1/me/absences")
				.queryParam("absenceTypeCode", "SICK")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, adminToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			Type listType = new TypeToken<List<AbsenceDto>>() {}.getType();
			List<AbsenceDto> list = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), listType);
			assertEquals(1, list.size());
			assertEquals("SICK", list.get(0).absenceTypeCode());
		}

		// 5. Cross-user access check: 'test' user tries to GET admin's absence -> 403 Forbidden
		try (Response response = target()
				.path("chronivaro/v1/me/absences/" + abs1Id)
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, testToken)
				.get()) {
			assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
		}

		// 6. Cross-user update check: 'test' user tries to PUT admin's absence -> 403 Forbidden
		String updateJson = """
				{
				  "absenceTypeCode": "VACATION",
				  "start": "2025-02-10T00:00:00+01:00",
				  "end": "2025-02-14T23:59:59+01:00",
				  "durationType": "full_day",
				  "comment": "Hacked Update"
				}
				""";
		try (Response response = target()
				.path("chronivaro/v1/me/absences/" + abs1Id)
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, testToken)
				.header(HttpHeaders.IF_MATCH, abs1Etag)
				.put(Entity.json(updateJson))) {
			assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
		}

		// 7. Cross-user cancel check: 'test' user tries to cancel admin's absence -> 403 Forbidden
		try (Response response = target()
				.path("chronivaro/v1/me/absences/" + abs1Id + "/cancel")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, testToken)
				.header(HttpHeaders.IF_MATCH, abs1Etag)
				.post(null)) {
			assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
		}

		// 8. Optimistic concurrency check: admin updates with stale If-Match -> 409 Conflict
		try (Response response = target()
				.path("chronivaro/v1/me/absences/" + abs1Id)
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, adminToken)
				.header(HttpHeaders.IF_MATCH, "\"99\"")
				.put(Entity.json(updateJson))) {
			assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
		}

		// 9. Legitimate update by admin with correct ETag -> 200 OK
		String updatedEtag;
		try (Response response = target()
				.path("chronivaro/v1/me/absences/" + abs1Id)
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, adminToken)
				.header(HttpHeaders.IF_MATCH, abs1Etag)
				.put(Entity.json(updateJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			updatedEtag = response.getHeaderString(HttpHeaders.ETAG);
			assertNotNull(updatedEtag);
			assertNotEquals(abs1Etag, updatedEtag);
		}

		// 10. Admin cancels own absence with correct updated ETag -> 200 OK
		try (Response response = target()
				.path("chronivaro/v1/me/absences/" + abs1Id + "/cancel")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, adminToken)
				.header(HttpHeaders.IF_MATCH, updatedEtag)
				.post(null)) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			AbsenceDto dto = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), AbsenceDto.class);
			assertEquals(STATE_CANCELLED, dto.state());
		}
	}
}
