package ch.eitchnet.chronivaro.rest;

import ch.eitchnet.chronivaro.core.service.CreditVacationEntitlementService;
import ch.eitchnet.chronivaro.core.service.PeriodActionArgument;
import ch.eitchnet.chronivaro.core.service.SubmitPeriodService;
import ch.eitchnet.chronivaro.rest.dto.AbsenceDto;
import ch.eitchnet.chronivaro.rest.dto.PeriodActionRequestDto;
import ch.eitchnet.chronivaro.rest.dto.PeriodStatusDto;
import ch.eitchnet.chronivaro.rest.resource.ChronivaroRestHelper;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class WebSupervisorApprovalsUiTest extends AbstractChronivaroRestfulTest {

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

			tx.commitOnClose();
		}

		// Credit vacation entitlement for 2026 for test employee
		CreditVacationEntitlementService.CreditVacationEntitlementArgument creditArg =
				new CreditVacationEntitlementService.CreditVacationEntitlementArgument();
		creditArg.year = 2026;
		creditArg.employeeId = "employee_emp";
		runtimeMock.getServiceHandler().doService(runtimeMock.loginAdmin(), new CreditVacationEntitlementService(), creditArg);
	}

	@Test
	public void shouldContainCompleteWebAssetsForSupervisorApprovals() throws IOException {
		File webDir = new File("../chronivaro-web/src/main/webapp");
		if (!webDir.exists()) {
			webDir = new File("chronivaro-web/src/main/webapp");
		}
		assertTrue("Web directory must exist", webDir.exists());

		File indexHtml = new File(webDir, "index.html");
		assertTrue("index.html must exist", indexHtml.exists());
		String indexContent = Files.readString(indexHtml.toPath());
		assertTrue("index.html must have approvals nav link", indexContent.contains("href=\"#approvals\""));
		assertTrue("index.html must have Approvals label", indexContent.contains("Approvals"));

		File appJs = new File(webDir, "js/app.js");
		assertTrue("app.js must exist", appJs.exists());
		String appJsContent = Files.readString(appJs.toPath());
		assertTrue("app.js must import ApprovalsView", appJsContent.contains("ApprovalsView"));
		assertTrue("app.js must route to approvals", appJsContent.contains("case 'approvals':"));

		File approvalsApiJs = new File(webDir, "js/api/ApprovalsApi.js");
		assertTrue("ApprovalsApi.js must exist", approvalsApiJs.exists());
		String apiContent = Files.readString(approvalsApiJs.toPath());
		assertTrue("ApprovalsApi must provide getSubmittedPeriods", apiContent.contains("getSubmittedPeriods"));
		assertTrue("ApprovalsApi must provide approvePeriod", apiContent.contains("approvePeriod"));
		assertTrue("ApprovalsApi must provide rejectPeriod", apiContent.contains("rejectPeriod"));
		assertTrue("ApprovalsApi must provide getSubmittedAbsences", apiContent.contains("getSubmittedAbsences"));
		assertTrue("ApprovalsApi must provide approveAbsence", apiContent.contains("approveAbsence"));
		assertTrue("ApprovalsApi must provide rejectAbsence", apiContent.contains("rejectAbsence"));

		File approvalsViewJs = new File(webDir, "js/pages/ApprovalsView.js");
		assertTrue("ApprovalsView.js must exist", approvalsViewJs.exists());
		String viewContent = Files.readString(approvalsViewJs.toPath());
		assertTrue("View must contain tab buttons", viewContent.contains("tab-absences-btn") && viewContent.contains("tab-periods-btn"));
		assertTrue("View must contain absence table", viewContent.contains("absences-table"));
		assertTrue("View must contain periods table", viewContent.contains("periods-table"));
		assertTrue("View must contain filter bars", viewContent.contains("absences-filter-bar") && viewContent.contains("periods-filter-bar"));
		assertTrue("View must contain pagination bars", viewContent.contains("absences-pagination") && viewContent.contains("periods-pagination"));
	}

	@Test
	public void shouldExecuteSupervisorAbsenceApprovalWorkflow() {
		String employeeToken = authenticate("employee", "admin");
		String supervisorToken = authenticate("supervisor", "admin");

		// 1. Employee requests absence
		String req1 = """
				{
				  "absenceTypeCode": "VACATION",
				  "start": "2026-06-01T08:00:00+02:00",
				  "end": "2026-06-01T17:00:00+02:00",
				  "durationType": "full_day",
				  "comment": "Summer vacation"
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

		// 2. Supervisor retrieves pending absences queue
		try (Response res = target().path("/chronivaro/v1/approvals/absences")
				.queryParam("teamId", "team-1")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());
			String json = res.readEntity(String.class);
			assertTrue("Must contain absence ID", json.contains(abs1Id));
			assertTrue("Must contain employeeId", json.contains("employee_emp"));
		}

		// 3. Supervisor approves absence
		try (Response approveRes = target().path("/chronivaro/v1/approvals/absences/" + abs1Id + "/approve")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.post(Entity.json("{}"))) {
			assertEquals(Response.Status.OK.getStatusCode(), approveRes.getStatus());
			AbsenceDto approvedDto = ChronivaroRestHelper.createGson().fromJson(
					approveRes.readEntity(String.class), AbsenceDto.class);
			assertEquals("APPROVED", approvedDto.state());
		}

		// 4. Request another absence for rejection testing
		String req2 = """
				{
				  "absenceTypeCode": "VACATION",
				  "start": "2026-06-10T08:00:00+02:00",
				  "end": "2026-06-11T17:00:00+02:00",
				  "durationType": "full_day",
				  "comment": "Another trip"
				}
				""";
		String abs2Id;
		try (Response response = target()
				.path("chronivaro/v1/me/absences")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, employeeToken)
				.post(Entity.json(req2))) {
			assertEquals(200, response.getStatus());
			AbsenceDto dto = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), AbsenceDto.class);
			abs2Id = dto.id();
		}

		// 5. Supervisor rejects absence with mandatory comment
		PeriodActionRequestDto rejectBody = new PeriodActionRequestDto("employee_emp", null, "Staffing conflict on June 10");
		try (Response rejectRes = target().path("/chronivaro/v1/approvals/absences/" + abs2Id + "/reject")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.post(Entity.json(ChronivaroRestHelper.createGson().toJson(rejectBody)))) {
			assertEquals(Response.Status.OK.getStatusCode(), rejectRes.getStatus());
			AbsenceDto rejectedDto = ChronivaroRestHelper.createGson().fromJson(
					rejectRes.readEntity(String.class), AbsenceDto.class);
			assertEquals("REJECTED", rejectedDto.state());
			assertEquals("Staffing conflict on June 10", rejectedDto.comment());
		}
	}

	@Test
	public void shouldExecuteSupervisorPeriodApprovalWorkflow() {
		String employeeToken = authenticate("employee", "admin");
		String supervisorToken = authenticate("supervisor", "admin");
		String yearMonth = "2026-08";

		// 1. Submit period for 2026-08 via REST
		PeriodActionRequestDto submitReq = new PeriodActionRequestDto("employee_emp", yearMonth, "Ready for review");
		try (Response submitRes = target().path("/chronivaro/v1/me/periods/" + yearMonth + "/submit")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, employeeToken)
				.post(Entity.json(ChronivaroRestHelper.createGson().toJson(submitReq)))) {
			assertEquals(Response.Status.OK.getStatusCode(), submitRes.getStatus());
		}

		String periodId = "period-employee_emp-" + yearMonth;

		// 2. Supervisor checks submitted periods queue
		try (Response res = target().path("/chronivaro/v1/approvals/periods")
				.queryParam("yearMonth", yearMonth)
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());
			String json = res.readEntity(String.class);
			assertTrue("Must contain employeeId", json.contains("employee_emp"));
			assertTrue("Must contain yearMonth", json.contains(yearMonth));
		}

		// 3. Supervisor rejects period with comment
		PeriodActionRequestDto rejectReq = new PeriodActionRequestDto("employee_emp", yearMonth, "Please review overtime balance");
		try (Response rejectRes = target().path("/chronivaro/v1/approvals/periods/" + periodId + "/reject")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.post(Entity.json(ChronivaroRestHelper.createGson().toJson(rejectReq)))) {
			assertEquals(Response.Status.OK.getStatusCode(), rejectRes.getStatus());
			PeriodStatusDto rejectedDto = ChronivaroRestHelper.createGson().fromJson(
					rejectRes.readEntity(String.class), PeriodStatusDto.class);
			assertEquals("REJECTED", rejectedDto.status());
			assertEquals("Please review overtime balance", rejectedDto.comment());
			assertEquals("supervisor", rejectedDto.rejectedBy());
		}

		// 4. Employee re-submits period
		PeriodActionRequestDto resubmitReq = new PeriodActionRequestDto("employee_emp", yearMonth, "Overtime adjusted");
		try (Response resubmitRes = target().path("/chronivaro/v1/me/periods/" + yearMonth + "/submit")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, employeeToken)
				.post(Entity.json(ChronivaroRestHelper.createGson().toJson(resubmitReq)))) {
			assertEquals(Response.Status.OK.getStatusCode(), resubmitRes.getStatus());
		}

		// 5. Supervisor approves period
		PeriodActionRequestDto approveReq = new PeriodActionRequestDto("employee_emp", yearMonth, "Looks good now");
		try (Response approveRes = target().path("/chronivaro/v1/approvals/periods/" + periodId + "/approve")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supervisorToken)
				.post(Entity.json(ChronivaroRestHelper.createGson().toJson(approveReq)))) {
			assertEquals(Response.Status.OK.getStatusCode(), approveRes.getStatus());
			PeriodStatusDto approvedDto = ChronivaroRestHelper.createGson().fromJson(
					approveRes.readEntity(String.class), PeriodStatusDto.class);
			assertEquals("APPROVED", approvedDto.status());
			assertEquals("supervisor", approvedDto.approvedBy());
		}
	}

	@Test
	public void shouldEnforceSupervisorAuthorizationAndRejectNonSupervisor() {
		String employeeToken = authenticate("employee", "admin");
		String yearMonth = "2026-08";

		// Submit period so it exists
		PeriodActionRequestDto submitReq = new PeriodActionRequestDto("employee_emp", yearMonth, "Ready for review");
		try (Response submitRes = target().path("/chronivaro/v1/me/periods/" + yearMonth + "/submit")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, employeeToken)
				.post(Entity.json(ChronivaroRestHelper.createGson().toJson(submitReq)))) {
			assertEquals(Response.Status.OK.getStatusCode(), submitRes.getStatus());
		}

		String periodId = "period-employee_emp-" + yearMonth;

		// Non-supervisor querying queue gets empty list because they supervise no employees
		try (Response res = target().path("/chronivaro/v1/approvals/absences")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, employeeToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());
			String json = res.readEntity(String.class);
			assertEquals("[]", json.trim());
		}

		// Non-supervisor attempting to approve is forbidden (403)
		try (Response res = target().path("/chronivaro/v1/approvals/periods/" + periodId + "/approve")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, employeeToken)
				.post(Entity.json("{}"))) {
			assertEquals(Response.Status.FORBIDDEN.getStatusCode(), res.getStatus());
		}

		// Unauthenticated request is rejected (401)
		try (Response res = target().path("/chronivaro/v1/approvals/absences")
				.request(MediaType.APPLICATION_JSON)
				.get()) {
			assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), res.getStatus());
		}
	}
}
