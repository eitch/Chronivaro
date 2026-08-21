package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.core.model.PeriodHelper;
import ch.atexxi.chronivaro.core.service.CreditVacationEntitlementService;
import ch.atexxi.chronivaro.core.service.PeriodActionArgument;
import ch.atexxi.chronivaro.core.service.RequestAbsenceService;
import ch.atexxi.chronivaro.core.service.SubmitPeriodService;
import ch.atexxi.chronivaro.rest.dto.AbsenceDto;
import ch.atexxi.chronivaro.rest.dto.PagedResultDto;
import ch.atexxi.chronivaro.rest.dto.PeriodStatusDto;
import ch.atexxi.chronivaro.rest.resource.ChronivaroRestHelper;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringResult;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class ApprovalsQueueTest extends AbstractChronivaroRestfulTest {

	private static final String ZONE = "Europe/Zurich";

	@Before
	public void setupTestData() {
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(runtimeMock.loginAdmin())) {
			tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY).forEach(tx::remove);
			tx.streamResources(TYPE_ABSENCE).forEach(tx::remove);
			tx.streamResources(TYPE_TIME_PERIOD).forEach(tx::remove);
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

			// Team 1
			Resource team1 = tx.getResourceBy(TYPE_TEAM, "team-1");
			if (team1 == null) {
				team1 = tx.getResourceTemplate(TYPE_TEAM, true);
				team1.setId("team-1");
				team1.setName("Engineering");
				tx.add(team1);
			}

			// Team 2
			Resource team2 = tx.getResourceBy(TYPE_TEAM, "team-2");
			if (team2 == null) {
				team2 = tx.getResourceTemplate(TYPE_TEAM, true);
				team2.setId("team-2");
				team2.setName("Marketing");
				tx.add(team2);
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

			// Employee 1 (Team 1, user: employee)
			Resource emp1 = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			emp1.setId("employee_emp");
			emp1.setName("Employee User");
			emp1.setString(PARAM_FIRSTNAME, "Employee");
			emp1.setString(PARAM_LASTNAME, "User");
			emp1.setString(PARAM_TIMEZONE, ZONE);
			emp1.setDate(PARAM_JOIN_DATE, LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of(ZONE)));
			emp1.setBoolean(PARAM_ACTIVE, true);
			emp1.setString(PARAM_USERNAME, "employee");
			emp1.setString(PARAM_USER_ID, "employee");
			emp1.setRelation(PARAM_LOCATION, loc);
			emp1.setRelation(PARAM_PRIMARY_TEAM, team1);

			Resource sched1 = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			sched1.setId("sched-emp1");
			sched1.setName("Schedule Emp1");
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

			// Supervisor (Team 1, user: supervisor)
			Resource sup = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			sup.setId("supervisor_emp");
			sup.setName("Supervisor User");
			sup.setString(PARAM_FIRSTNAME, "Supervisor");
			sup.setString(PARAM_LASTNAME, "User");
			sup.setString(PARAM_TIMEZONE, ZONE);
			sup.setDate(PARAM_JOIN_DATE, LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of(ZONE)));
			sup.setBoolean(PARAM_ACTIVE, true);
			sup.setString(PARAM_USERNAME, "supervisor");
			sup.setString(PARAM_USER_ID, "supervisor");
			sup.setRelation(PARAM_LOCATION, loc);
			sup.setRelation(PARAM_PRIMARY_TEAM, team1);

			Resource schedSup = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			schedSup.setId("sched-sup");
			schedSup.setName("Schedule Sup");
			schedSup.setRelation(PARAM_EMPLOYEE, sup);
			schedSup.setDate(PARAM_VALID_FROM, LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of(ZONE)));
			schedSup.setDouble(PARAM_EMPLOYMENT_RATE, 1.0);
			schedSup.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, 480);
			schedSup.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, 480);
			schedSup.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, 480);
			schedSup.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, 480);
			schedSup.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, 480);
			schedSup.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, 0);
			schedSup.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, 0);
			schedSup.setInteger(PARAM_WEEKLY_TARGET_MINUTES, 2400);
			tx.add(schedSup);
			sup.setRelation(PARAM_CURRENT_SCHEDULE, schedSup);
			tx.add(sup);

			// Marketing Employee (Team 2)
			Resource emp2 = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			emp2.setId("marketing_emp");
			emp2.setName("Marketing User");
			emp2.setString(PARAM_FIRSTNAME, "Marketing");
			emp2.setString(PARAM_LASTNAME, "User");
			emp2.setString(PARAM_TIMEZONE, ZONE);
			emp2.setDate(PARAM_JOIN_DATE, LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of(ZONE)));
			emp2.setBoolean(PARAM_ACTIVE, true);
			emp2.setString(PARAM_USERNAME, "marketing_user");
			emp2.setString(PARAM_USER_ID, "marketing_user");
			emp2.setRelation(PARAM_LOCATION, loc);
			emp2.setRelation(PARAM_PRIMARY_TEAM, team2);

			Resource sched2 = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			sched2.setId("sched-emp2");
			sched2.setName("Schedule Emp2");
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

		// Credit vacation entitlement for 2026 for all three test employees
		CreditVacationEntitlementService.CreditVacationEntitlementArgument creditArg =
				new CreditVacationEntitlementService.CreditVacationEntitlementArgument();
		creditArg.year = 2026;
		creditArg.employeeId = "employee_emp";
		runtimeMock.getServiceHandler().doService(runtimeMock.loginAdmin(), new CreditVacationEntitlementService(), creditArg);

		creditArg.employeeId = "supervisor_emp";
		runtimeMock.getServiceHandler().doService(runtimeMock.loginAdmin(), new CreditVacationEntitlementService(), creditArg);

		creditArg.employeeId = "marketing_emp";
		runtimeMock.getServiceHandler().doService(runtimeMock.loginAdmin(), new CreditVacationEntitlementService(), creditArg);
	}

	@Test
	public void testSupervisorApprovalQueueAbsenceScopingAndActions() {
		String employeeToken = authenticate("employee", "admin");
		String supervisorToken = authenticate("supervisor", "admin");
		String hrToken = authenticate("hr", "admin");
		String adminToken = authenticate("admin", "admin");

		// 1. employee_emp (Team 1) requests absence
		String req1 = """
				{
				  "absenceTypeCode": "VACATION",
				  "start": "2026-06-01T08:00:00+02:00",
				  "end": "2026-06-01T17:00:00+02:00",
				  "durationType": "full_day",
				  "comment": "Employee vacation"
				}
				""";
		String abs1Id;
		try (Response response = target()
				.path("chronivaro/v1/me/absences")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, employeeToken)
				.post(Entity.json(req1))) {
			assertEquals(200, response.getStatus());
			AbsenceDto dto = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), AbsenceDto.class);
			abs1Id = dto.id();
		}

		// 2. admin requests absence for marketing_emp (Team 2)
		RequestAbsenceService.RequestAbsenceArgument req2 = new RequestAbsenceService.RequestAbsenceArgument();
		req2.employeeId = "marketing_emp";
		req2.absenceTypeCode = "VACATION";
		req2.start = ZonedDateTime.parse("2026-06-02T08:00:00+02:00");
		req2.end = ZonedDateTime.parse("2026-06-02T17:00:00+02:00");
		req2.durationType = "FULL_DAY";
		req2.comment = "Marketing vacation";
		StringResult res2 = runtimeMock.getServiceHandler().doService(runtimeMock.loginAdmin(), new RequestAbsenceService(), req2);
		assertTrue(res2.isOk());
		String abs2Id = res2.getValue();

		// 3. supervisor_emp (Team 1) requests absence
		String req3 = """
				{
				  "absenceTypeCode": "VACATION",
				  "start": "2026-06-03T08:00:00+02:00",
				  "end": "2026-06-03T17:00:00+02:00",
				  "durationType": "full_day",
				  "comment": "Supervisor vacation"
				}
				""";
		String abs3Id;
		try (Response response = target()
				.path("chronivaro/v1/me/absences")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.post(Entity.json(req3))) {
			assertEquals(200, response.getStatus());
			AbsenceDto dto = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), AbsenceDto.class);
			abs3Id = dto.id();
		}

		// 4. Supervisor queries approval queue
		try (Response response = target()
				.path("chronivaro/v1/approvals/absences")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.get()) {
			assertEquals(200, response.getStatus());
			List<AbsenceDto> list = ChronivaroRestHelper.createGson().fromJson(
					response.readEntity(String.class),
					new TypeToken<List<AbsenceDto>>() {}.getType());

			List<String> ids = list.stream().map(AbsenceDto::id).toList();
			assertTrue("Supervisor queue must contain Team 1 employee absence", ids.contains(abs1Id));
			assertTrue("Supervisor queue must contain Team 1 supervisor absence", ids.contains(abs3Id));
			assertFalse("Supervisor queue must NOT contain Team 2 marketing absence", ids.contains(abs2Id));

			AbsenceDto abs1Dto = list.stream().filter(a -> a.id().equals(abs1Id)).findFirst().orElse(null);
			assertNotNull(abs1Dto);
			assertEquals("Employee User", abs1Dto.employeeName());
			assertEquals("Engineering", abs1Dto.teamName());
			assertEquals("Vacation", abs1Dto.absenceTypeName());
		}

		// 5. Supervisor filters with teamId=team-2 (not supervised) -> returns empty
		try (Response response = target()
				.path("chronivaro/v1/approvals/absences")
				.queryParam("teamId", "team-2")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.get()) {
			assertEquals(200, response.getStatus());
			List<AbsenceDto> list = ChronivaroRestHelper.createGson().fromJson(
					response.readEntity(String.class),
					new TypeToken<List<AbsenceDto>>() {}.getType());
			assertTrue("Supervisor filtered by team-2 must be empty", list.isEmpty());
		}

		// 6. Supervisor filters with pagination
		try (Response response = target()
				.path("chronivaro/v1/approvals/absences")
				.queryParam("offset", 0)
				.queryParam("limit", 1)
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.get()) {
			assertEquals(200, response.getStatus());
			PagedResultDto<AbsenceDto> paged = ChronivaroRestHelper.createGson().fromJson(
					response.readEntity(String.class),
					new TypeToken<PagedResultDto<AbsenceDto>>() {}.getType());
			assertEquals(1, paged.size());
			assertTrue(paged.total() >= 2);
		}

		// 7. Supervisor approves abs1 (Team 1 member) -> SUCCESS
		try (Response response = target()
				.path("chronivaro/v1/approvals/absences/" + abs1Id + "/approve")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.post(Entity.json(""))) {
			assertEquals(200, response.getStatus());
			AbsenceDto dto = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), AbsenceDto.class);
			assertEquals("APPROVED", dto.state());
		}

		// 8. Supervisor attempts to approve abs2 (Team 2 marketing member) -> 403 FORBIDDEN
		try (Response response = target()
				.path("chronivaro/v1/approvals/absences/" + abs2Id + "/approve")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.post(Entity.json(""))) {
			assertEquals(403, response.getStatus());
		}

		// 9. Supervisor attempts to approve own absence (abs3) -> 403 FORBIDDEN (self-approval prohibited)
		try (Response response = target()
				.path("chronivaro/v1/approvals/absences/" + abs3Id + "/approve")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.post(Entity.json(""))) {
			assertEquals(403, response.getStatus());
		}

		// 10. HR user queries approvals -> sees abs2 and abs3
		try (Response response = target()
				.path("chronivaro/v1/approvals/absences")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, hrToken)
				.get()) {
			assertEquals(200, response.getStatus());
			List<AbsenceDto> list = ChronivaroRestHelper.createGson().fromJson(
					response.readEntity(String.class),
					new TypeToken<List<AbsenceDto>>() {}.getType());
			List<String> ids = list.stream().map(AbsenceDto::id).toList();
			assertTrue(ids.contains(abs2Id));
			assertTrue(ids.contains(abs3Id));
		}

		// 11. HR user approves abs2 and abs3 -> SUCCESS
		try (Response response = target()
				.path("chronivaro/v1/approvals/absences/" + abs2Id + "/approve")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, hrToken)
				.post(Entity.json(""))) {
			assertEquals(200, response.getStatus());
		}
		try (Response response = target()
				.path("chronivaro/v1/approvals/absences/" + abs3Id + "/approve")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, hrToken)
				.post(Entity.json(""))) {
			assertEquals(200, response.getStatus());
		}
	}

	@Test
	public void testSupervisorApprovalQueuePeriodScopingAndActions() {
		String employeeToken = authenticate("employee", "admin");
		String supervisorToken = authenticate("supervisor", "admin");
		String hrToken = authenticate("hr", "admin");
		String adminToken = authenticate("admin", "admin");

		// 1. Submit period 2026-03 for employee_emp (Team 1)
		JsonObject submitReq = new JsonObject();
		submitReq.addProperty("comment", "Emp March Submit");
		try (Response response = target()
				.path("chronivaro/v1/me/periods/2026-03/submit")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, employeeToken)
				.post(Entity.json(submitReq.toString()))) {
			assertEquals(200, response.getStatus());
		}

		// 2. Submit period 2026-03 for supervisor_emp (Team 1)
		try (Response response = target()
				.path("chronivaro/v1/me/periods/2026-03/submit")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.post(Entity.json(submitReq.toString()))) {
			assertEquals(200, response.getStatus());
		}

		// 3. Admin submits period 2026-03 for marketing_emp (Team 2)
		PeriodActionArgument periodArg =
				new PeriodActionArgument("marketing_emp", YearMonth.of(2026, 3), "Marketing March Submit");
		runtimeMock.getServiceHandler().doService(runtimeMock.loginAdmin(), new SubmitPeriodService(), periodArg);

		// 4. Supervisor queries approval queue periods
		try (Response response = target()
				.path("chronivaro/v1/approvals/periods")
				.queryParam("yearMonth", "2026-03")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.get()) {
			assertEquals(200, response.getStatus());
			List<PeriodStatusDto> list = ChronivaroRestHelper.createGson().fromJson(
					response.readEntity(String.class),
					new TypeToken<List<PeriodStatusDto>>() {}.getType());

			List<String> empIds = list.stream().map(PeriodStatusDto::employeeId).toList();
			assertTrue("Supervisor sees Team 1 employee period", empIds.contains("employee_emp"));
			assertTrue("Supervisor sees Team 1 supervisor period", empIds.contains("supervisor_emp"));
			assertFalse("Supervisor must NOT see Team 2 marketing period", empIds.contains("marketing_emp"));

			PeriodStatusDto emp1Period = list.stream().filter(p -> p.employeeId().equals("employee_emp")).findFirst().orElse(null);
			assertNotNull(emp1Period);
			assertEquals("Employee User", emp1Period.employeeName());
			assertEquals("Engineering", emp1Period.teamName());
		}

		String emp1PeriodId = PeriodHelper.getPeriodId("employee_emp", YearMonth.of(2026, 3));
		String supPeriodId = PeriodHelper.getPeriodId("supervisor_emp", YearMonth.of(2026, 3));
		String mktPeriodId = PeriodHelper.getPeriodId("marketing_emp", YearMonth.of(2026, 3));

		// 5. Supervisor approves employee_emp period -> SUCCESS
		JsonObject approveReq = new JsonObject();
		approveReq.addProperty("comment", "Approved by supervisor");
		try (Response response = target()
				.path("chronivaro/v1/approvals/periods/" + emp1PeriodId + "/approve")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.post(Entity.json(approveReq.toString()))) {
			assertEquals(200, response.getStatus());
			PeriodStatusDto dto = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), PeriodStatusDto.class);
			assertEquals("APPROVED", dto.status());
		}

		// 6. Supervisor attempts to approve marketing_emp period -> 403 FORBIDDEN
		try (Response response = target()
				.path("chronivaro/v1/approvals/periods/" + mktPeriodId + "/approve")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.post(Entity.json(approveReq.toString()))) {
			assertEquals(403, response.getStatus());
		}

		// 7. Supervisor attempts to approve own period -> 403 FORBIDDEN (self-approval prohibited)
		try (Response response = target()
				.path("chronivaro/v1/approvals/periods/" + supPeriodId + "/approve")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.post(Entity.json(approveReq.toString()))) {
			assertEquals(403, response.getStatus());
		}

		// 8. HR user approves marketing_emp and supervisor_emp periods -> SUCCESS
		try (Response response = target()
				.path("chronivaro/v1/approvals/periods/" + mktPeriodId + "/approve")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, hrToken)
				.post(Entity.json(approveReq.toString()))) {
			assertEquals(200, response.getStatus());
		}
		try (Response response = target()
				.path("chronivaro/v1/approvals/periods/" + supPeriodId + "/approve")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, hrToken)
				.post(Entity.json(approveReq.toString()))) {
			assertEquals(200, response.getStatus());
		}
	}

	@Test
	public void testSupervisorRejectionsAndValidation() {
		String employeeToken = authenticate("employee", "admin");
		String supervisorToken = authenticate("supervisor", "admin");

		// 1. Employee requests absence
		String req = """
				{
				  "absenceTypeCode": "VACATION",
				  "start": "2026-07-01T08:00:00+02:00",
				  "end": "2026-07-01T17:00:00+02:00",
				  "durationType": "full_day",
				  "comment": "July vacation"
				}
				""";
		String absId;
		try (Response response = target()
				.path("chronivaro/v1/me/absences")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, employeeToken)
				.post(Entity.json(req))) {
			assertEquals(200, response.getStatus());
			AbsenceDto dto = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), AbsenceDto.class);
			absId = dto.id();
		}

		// 2. Reject absence without comment -> 400 Bad Request
		JsonObject rejectNoComment = new JsonObject();
		try (Response response = target()
				.path("chronivaro/v1/approvals/absences/" + absId + "/reject")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.post(Entity.json(rejectNoComment.toString()))) {
			assertEquals(400, response.getStatus());
		}

		// 3. Reject absence with comment -> SUCCESS
		JsonObject rejectReq = new JsonObject();
		rejectReq.addProperty("comment", "Staffing shortage on that day");
		try (Response response = target()
				.path("chronivaro/v1/approvals/absences/" + absId + "/reject")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.post(Entity.json(rejectReq.toString()))) {
			assertEquals(200, response.getStatus());
			AbsenceDto dto = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), AbsenceDto.class);
			assertEquals("REJECTED", dto.state());
			assertEquals("Staffing shortage on that day", dto.comment());
		}

		// 4. Submit period 2026-04 and reject with comment
		JsonObject submitReq = new JsonObject();
		submitReq.addProperty("comment", "April submit");
		try (Response response = target()
				.path("chronivaro/v1/me/periods/2026-04/submit")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, employeeToken)
				.post(Entity.json(submitReq.toString()))) {
			assertEquals(200, response.getStatus());
		}

		String emp1AprilPeriodId = PeriodHelper.getPeriodId("employee_emp", YearMonth.of(2026, 4));
		JsonObject rejectPeriodReq = new JsonObject();
		rejectPeriodReq.addProperty("comment", "Missing work entry on April 15");
		try (Response response = target()
				.path("chronivaro/v1/approvals/periods/" + emp1AprilPeriodId + "/reject")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.post(Entity.json(rejectPeriodReq.toString()))) {
			assertEquals(200, response.getStatus());
			PeriodStatusDto dto = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), PeriodStatusDto.class);
			assertEquals("REJECTED", dto.status());
		}
	}
}
