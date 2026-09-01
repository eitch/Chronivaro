package ch.eitchnet.chronivaro.rest;

import ch.eitchnet.chronivaro.core.report.CsvExportHelper;
import ch.eitchnet.chronivaro.core.service.CreditVacationEntitlementService;
import ch.eitchnet.chronivaro.rest.resource.ChronivaroRestHelper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class ReportsResourceTest extends AbstractChronivaroRestfulTest {

	private static final String ZONE = "Europe/Zurich";

	@Before
	public void setupTestData() {
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(runtimeMock.loginAdmin())) {
			tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY).forEach(tx::remove);
			tx.streamResources(TYPE_WORK_ENTRY).forEach(tx::remove);
			tx.streamResources(TYPE_WORK_DAY).forEach(tx::remove);
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

			// Team 1 (Supervised by supervisor_user)
			Resource team1 = tx.getResourceBy(TYPE_TEAM, "team-1");
			if (team1 == null) {
				team1 = tx.getResourceTemplate(TYPE_TEAM, true);
				team1.setId("team-1");
				team1.setName("Engineering");
				team1.setString(PARAM_NAME, "Engineering");
				tx.add(team1);
			}

			// Team 2 (Other team)
			Resource team2 = tx.getResourceBy(TYPE_TEAM, "team-2");
			if (team2 == null) {
				team2 = tx.getResourceTemplate(TYPE_TEAM, true);
				team2.setId("team-2");
				team2.setName("Marketing");
				team2.setString(PARAM_NAME, "Marketing");
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

			emp1.setRelation(PARAM_CURRENT_SCHEDULE, sched1);
			tx.add(sched1);
			tx.add(emp1);

			// Supervisor Employee (Team 1, user: supervisor)
			Resource supervisorEmp = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			supervisorEmp.setId("supervisor_emp");
			supervisorEmp.setName("Supervisor User");
			supervisorEmp.setString(PARAM_FIRSTNAME, "Supervisor");
			supervisorEmp.setString(PARAM_LASTNAME, "User");
			supervisorEmp.setString(PARAM_TIMEZONE, ZONE);
			supervisorEmp.setDate(PARAM_JOIN_DATE, LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of(ZONE)));
			supervisorEmp.setBoolean(PARAM_ACTIVE, true);
			supervisorEmp.setString(PARAM_USERNAME, "supervisor");
			supervisorEmp.setString(PARAM_USER_ID, "supervisor");
			supervisorEmp.setRelation(PARAM_LOCATION, loc);
			supervisorEmp.setRelation(PARAM_PRIMARY_TEAM, team1);

			Resource schedSup = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			schedSup.setId("sched-sup");
			schedSup.setName("Schedule Supervisor");
			schedSup.setRelation(PARAM_EMPLOYEE, supervisorEmp);
			schedSup.setDate(PARAM_VALID_FROM, LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of(ZONE)));
			schedSup.setDouble(PARAM_EMPLOYMENT_RATE, 1.0);
			schedSup.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, 480);
			schedSup.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, 480);
			schedSup.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, 480);
			schedSup.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, 480);
			schedSup.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, 480);
			schedSup.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, 0);
			schedSup.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, 0);

			supervisorEmp.setRelation(PARAM_CURRENT_SCHEDULE, schedSup);
			tx.add(schedSup);
			tx.add(supervisorEmp);

			// Other Employee (Team 2)
			Resource otherEmp = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			otherEmp.setId("other_emp");
			otherEmp.setName("Other Team User");
			otherEmp.setString(PARAM_FIRSTNAME, "Other");
			otherEmp.setString(PARAM_LASTNAME, "TeamUser");
			otherEmp.setString(PARAM_TIMEZONE, ZONE);
			otherEmp.setDate(PARAM_JOIN_DATE, LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of(ZONE)));
			otherEmp.setBoolean(PARAM_ACTIVE, true);
			otherEmp.setString(PARAM_USERNAME, "other_user");
			otherEmp.setString(PARAM_USER_ID, "other_user");
			otherEmp.setRelation(PARAM_LOCATION, loc);
			otherEmp.setRelation(PARAM_PRIMARY_TEAM, team2);

			Resource schedOther = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			schedOther.setId("sched-other");
			schedOther.setName("Schedule Other");
			schedOther.setRelation(PARAM_EMPLOYEE, otherEmp);
			schedOther.setDate(PARAM_VALID_FROM, LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of(ZONE)));
			schedOther.setDouble(PARAM_EMPLOYMENT_RATE, 1.0);
			schedOther.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, 480);
			schedOther.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, 480);
			schedOther.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, 480);
			schedOther.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, 480);
			schedOther.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, 480);
			schedOther.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, 0);
			schedOther.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, 0);

			otherEmp.setRelation(PARAM_CURRENT_SCHEDULE, schedOther);
			tx.add(schedOther);
			tx.add(otherEmp);

			// Add work entry for employee_emp on 2026-08-03
			Resource workDay = tx.getResourceTemplate(TYPE_WORK_DAY, true);
			workDay.setId("wd-employee_emp-2026-08-03");
			workDay.setRelationId(PARAM_EMPLOYEE, "employee_emp");
			workDay.setDate(PARAM_DATE, LocalDate.of(2026, 8, 3).atStartOfDay(ZoneId.of(ZONE)));

			Resource workEntry = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
			workEntry.setId("we-report-1");
			workEntry.setRelationId(PARAM_EMPLOYEE, "employee_emp");
			workEntry.setRelation(PARAM_WORK_DAY, workDay);
			workEntry.setDate(PARAM_START, ZonedDateTime.parse("2026-08-03T08:00:00+02:00[Europe/Zurich]"));
			workEntry.setDate(PARAM_END, ZonedDateTime.parse("2026-08-03T17:00:00+02:00[Europe/Zurich]"));
			workEntry.setString(PARAM_WORKING_LOCATION, "OFFICE");
			workEntry.setString(PARAM_SOURCE, "MANUAL");
			workEntry.setString(PARAM_COMMENT, "Regular workday");
			workEntry.setString(PARAM_CREATED_BY, "employee");
			tx.add(workEntry);

			workDay.addRelation(PARAM_WORK_ENTRIES, workEntry);
			tx.add(workDay);

			// Add an Absence
			Resource absence = tx.getResourceTemplate(TYPE_ABSENCE, true);
			absence.setId("abs-report-1");
			absence.setName("Vacation");
			absence.setRelationId(PARAM_EMPLOYEE, "employee_emp");
			absence.setRelationId(PARAM_ABSENCE_TYPE, "vacation");
			absence.setString(PARAM_ABSENCE_TYPE, "VACATION");
			absence.setDate(PARAM_START, LocalDate.of(2026, 8, 10).atStartOfDay(ZoneId.of(ZONE)));
			absence.setDate(PARAM_END, LocalDate.of(2026, 8, 14).atTime(23, 59, 59).atZone(ZoneId.of(ZONE)));
			absence.setString(PARAM_DURATION_TYPE, DURATION_FULL_DAY);
			absence.setInteger(PARAM_MINUTES, 2400);
			absence.setString(PARAM_STATE, STATE_APPROVED);
			absence.setString(PARAM_COMMENT, "Summer break");
			absence.setDate(PARAM_SUBMITTED_AT, ZonedDateTime.now());
			absence.setDate(PARAM_APPROVED_AT, ZonedDateTime.now());
			absence.setString(PARAM_APPROVED_BY, "supervisor");
			tx.add(absence);

			tx.commitOnClose();
		}

		// Credit vacation entitlement for 2026
		CreditVacationEntitlementService creditService = new CreditVacationEntitlementService();
		CreditVacationEntitlementService.CreditVacationEntitlementArgument arg = new CreditVacationEntitlementService.CreditVacationEntitlementArgument();
		arg.employeeId = "employee_emp";
		arg.year = 2026;
		runtimeMock.getServiceHandler().doService(runtimeMock.loginAdmin(), creditService, arg);
	}

	@Test
	public void shouldGetDayReportAsJsonAndCsv() {
		String employeeToken = authenticate("employee", "admin");

		// 1. JSON Day Report
		try (Response resJson = target()
				.path("chronivaro/v1/reports/day")
				.queryParam("date", "2026-08-03")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(200, resJson.getStatus());
			String jsonStr = resJson.readEntity(String.class);
			JsonObject dayObj = JsonParser.parseString(jsonStr).getAsJsonObject();
			assertEquals("2026-08-03", dayObj.get("date").getAsString());
			assertEquals(480, dayObj.get("targetMinutes").getAsInt());
			assertEquals(540, dayObj.get("actualMinutes").getAsInt());
		}

		// 2. CSV Day Report via query param
		try (Response resCsv = target()
				.path("chronivaro/v1/reports/day")
				.queryParam("date", "2026-08-03")
				.queryParam("format", "csv")
				.request()
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(200, resCsv.getStatus());
			assertTrue(resCsv.getMediaType().toString().contains("text/csv"));
			String csvStr = resCsv.readEntity(String.class);
			assertTrue(csvStr.startsWith(CsvExportHelper.UTF8_BOM));
			assertTrue(csvStr.contains("Date,EmployeeId,EmployeeName,TargetMinutes,ActualMinutes"));
			assertTrue(csvStr.contains("employee_emp"));
			assertTrue(csvStr.contains("WorkEntries:"));
		}

		// 3. Unauthorized access by employee to another employee
		try (Response resForbidden = target()
				.path("chronivaro/v1/reports/day")
				.queryParam("date", "2026-08-03")
				.queryParam("employeeId", "other_emp")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(403, resForbidden.getStatus());
		}
	}

	@Test
	public void shouldGetMonthReportAsJsonAndCsv() {
		String employeeToken = authenticate("employee", "admin");

		// 1. JSON Month Report
		try (Response resJson = target()
				.path("chronivaro/v1/reports/month")
				.queryParam("yearMonth", "2026-08")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(200, resJson.getStatus());
			String jsonStr = resJson.readEntity(String.class);
			JsonObject monthObj = JsonParser.parseString(jsonStr).getAsJsonObject();
			assertEquals("employee_emp", monthObj.get("employeeId").getAsString());
			assertEquals("2026-08", monthObj.get("yearMonth").getAsString());
			assertTrue(monthObj.get("totalTargetMinutes").getAsInt() > 0);
			assertTrue(monthObj.get("totalActualMinutes").getAsInt() > 0);
		}

		// 2. CSV Month Report
		try (Response resCsv = target()
				.path("chronivaro/v1/reports/month")
				.queryParam("yearMonth", "2026-08")
				.queryParam("format", "csv")
				.request()
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(200, resCsv.getStatus());
			assertTrue(resCsv.getMediaType().toString().contains("text/csv"));
			String csvStr = resCsv.readEntity(String.class);
			assertTrue(csvStr.startsWith(CsvExportHelper.UTF8_BOM));
			assertTrue(csvStr.contains("Date,DayOfWeek,TargetMinutes,ActualMinutes,HolidayMinutes,AbsenceMinutes"));
			assertTrue(csvStr.contains("EmployeeId,EmployeeName,YearMonth,TotalTargetMinutes"));
		}
	}

	@Test
	public void shouldGetVacationReportAsJsonAndCsv() {
		String employeeToken = authenticate("employee", "admin");

		// 1. JSON Vacation Report
		try (Response resJson = target()
				.path("chronivaro/v1/reports/vacation")
				.queryParam("year", 2026)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(200, resJson.getStatus());
			String jsonStr = resJson.readEntity(String.class);
			JsonObject vacObj = JsonParser.parseString(jsonStr).getAsJsonObject();
			assertEquals("employee_emp", vacObj.get("employeeId").getAsString());
			assertEquals("employee", vacObj.get("username").getAsString());
			assertEquals(2026, vacObj.get("year").getAsInt());
			assertEquals(12000, vacObj.get("entitlementMinutes").getAsInt());
			assertTrue(vacObj.get("entries").getAsJsonArray().size() > 0);
		}

		// 2. CSV Vacation Report
		try (Response resCsv = target()
				.path("chronivaro/v1/reports/vacation")
				.queryParam("year", 2026)
				.queryParam("format", "csv")
				.request()
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(200, resCsv.getStatus());
			String csvStr = resCsv.readEntity(String.class);
			assertTrue(csvStr.startsWith(CsvExportHelper.UTF8_BOM));
			assertTrue(csvStr.contains("AnnualEntitlementMinutes"));
			assertTrue(csvStr.contains("JournalEntries:"));
		}
	}

	@Test
	public void shouldGetVacationReportForNewlyCreatedEmployeeWithoutBookings() {
		// Create a new employee without vacation bookings
		String adminToken = authenticate("admin", "admin");
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(runtimeMock.loginAdmin())) {
			Resource newEmp = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			newEmp.setId("newbie_emp");
			newEmp.setName("Newbie User");
			newEmp.setString(PARAM_FIRSTNAME, "Newbie");
			newEmp.setString(PARAM_LASTNAME, "User");
			newEmp.setString(PARAM_USERNAME, "newbie");
			newEmp.setString(PARAM_PERSONAL_NUMBER, "9999");
			newEmp.setDate(PARAM_JOIN_DATE, LocalDate.of(2026, 1, 1).atStartOfDay(ZoneId.of(ZONE)));
			newEmp.setRelation(PARAM_PRIMARY_TEAM, tx.getResourceBy(TYPE_TEAM, "team-1"));
			newEmp.setRelation(PARAM_LOCATION, tx.getResourceBy(TYPE_LOCATION, "test-loc"));
			newEmp.setBoolean(PARAM_ACTIVE, true);
			tx.add(newEmp);
			tx.commitOnClose();
		}

		// 1. JSON Vacation Report for newly created employee
		try (Response resJson = target()
				.path("chronivaro/v1/reports/vacation")
				.queryParam("employeeId", "newbie_emp")
				.queryParam("year", 2026)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.get()) {

			assertEquals(200, resJson.getStatus());
			String jsonStr = resJson.readEntity(String.class);
			JsonObject vacObj = JsonParser.parseString(jsonStr).getAsJsonObject();
			assertEquals("newbie_emp", vacObj.get("employeeId").getAsString());
			assertEquals("Newbie User", vacObj.get("employeeName").getAsString());
			assertEquals(2026, vacObj.get("year").getAsInt());
			assertEquals(0, vacObj.get("carryOverMinutes").getAsInt());
			assertEquals(0, vacObj.get("entitlementMinutes").getAsInt());
			assertEquals(0, vacObj.get("correctionsMinutes").getAsInt());
			assertEquals(0, vacObj.get("usageMinutes").getAsInt());
			assertEquals(0, vacObj.get("remainingMinutes").getAsInt());
			assertNotNull(vacObj.get("entries"));
			assertEquals(0, vacObj.get("entries").getAsJsonArray().size());
		}

		// 2. CSV Vacation Report for newly created employee
		try (Response resCsv = target()
				.path("chronivaro/v1/reports/vacation")
				.queryParam("employeeId", "newbie_emp")
				.queryParam("year", 2026)
				.queryParam("format", "csv")
				.request()
				.header("Authorization", adminToken)
				.get()) {

			assertEquals(200, resCsv.getStatus());
			String csvStr = resCsv.readEntity(String.class);
			assertTrue(csvStr.startsWith(CsvExportHelper.UTF8_BOM));
			assertTrue(csvStr.contains("newbie_emp,Newbie User,2026,0,0,0,0,0,00:00"));
			assertTrue(csvStr.contains("JournalEntries:"));
		}

		// 3. PDF Vacation Report for newly created employee
		try (Response resPdf = target()
				.path("chronivaro/v1/reports/vacation")
				.queryParam("employeeId", "newbie_emp")
				.queryParam("year", 2026)
				.queryParam("format", "pdf")
				.request()
				.header("Authorization", adminToken)
				.get()) {

			assertEquals(200, resPdf.getStatus());
			assertTrue(resPdf.getMediaType().toString().startsWith("application/pdf"));
			byte[] pdfBytes = resPdf.readEntity(byte[].class);
			assertNotNull(pdfBytes);
			assertTrue(pdfBytes.length > 500);
			assertTrue(new String(pdfBytes, 0, 5).startsWith("%PDF-"));
		}
	}

	@Test
	public void shouldGetTeamReportWithScopingAndMissingBookings() {
		String supervisorToken = authenticate("supervisor", "admin");
		String employeeToken = authenticate("employee", "admin");

		// 1. Supervisor queries supervised team-1 -> 200 OK
		try (Response resSup = target()
				.path("chronivaro/v1/reports/team")
				.queryParam("teamId", "team-1")
				.queryParam("yearMonth", "2026-08")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", supervisorToken)
				.get()) {

			assertEquals(200, resSup.getStatus());
			String jsonStr = resSup.readEntity(String.class);
			JsonObject teamReport = JsonParser.parseString(jsonStr).getAsJsonObject();
			assertEquals("team-1", teamReport.get("teamId").getAsString());
			assertEquals(2, teamReport.get("employees").getAsJsonArray().size()); // employee_emp and supervisor_emp
		}

		// 2. Supervisor queries team-2 (not supervised) -> 403 Forbidden
		try (Response resForbidden = target()
				.path("chronivaro/v1/reports/team")
				.queryParam("teamId", "team-2")
				.queryParam("yearMonth", "2026-08")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", supervisorToken)
				.get()) {

			assertEquals(403, resForbidden.getStatus());
		}

		// 3. Regular employee queries team report -> 403 Forbidden
		try (Response resEmpForbidden = target()
				.path("chronivaro/v1/reports/team")
				.queryParam("teamId", "team-1")
				.queryParam("yearMonth", "2026-08")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(403, resEmpForbidden.getStatus());
		}

		// 4. CSV export of team report
		try (Response resCsv = target()
				.path("chronivaro/v1/reports/team")
				.queryParam("teamId", "team-1")
				.queryParam("yearMonth", "2026-08")
				.queryParam("format", "csv")
				.request()
				.header("Authorization", supervisorToken)
				.get()) {

			assertEquals(200, resCsv.getStatus());
			String csvStr = resCsv.readEntity(String.class);
			assertTrue(csvStr.startsWith(CsvExportHelper.UTF8_BOM));
			assertTrue(csvStr.contains("EmployeeId,EmployeeName,TeamId,YearMonth"));
			assertTrue(csvStr.contains("employee_emp"));
		}
	}

	@Test
	public void shouldGetAbsencesReportWithFilteringAndScoping() {
		String employeeToken = authenticate("employee", "admin");
		String supervisorToken = authenticate("supervisor", "admin");

		// 1. Employee gets own absence report
		try (Response resEmp = target()
				.path("chronivaro/v1/reports/absences")
				.queryParam("from", "2026-08-01")
				.queryParam("to", "2026-08-31")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(200, resEmp.getStatus());
			String jsonStr = resEmp.readEntity(String.class);
			JsonObject report = JsonParser.parseString(jsonStr).getAsJsonObject();
			assertEquals(1, report.get("items").getAsJsonArray().size());
			assertEquals("employee_emp", report.get("items").getAsJsonArray().get(0).getAsJsonObject().get("employeeId").getAsString());
		}

		// 2. Supervisor gets absences report for team
		try (Response resSup = target()
				.path("chronivaro/v1/reports/absences")
				.queryParam("teamId", "team-1")
				.queryParam("format", "csv")
				.request()
				.header("Authorization", supervisorToken)
				.get()) {

			assertEquals(200, resSup.getStatus());
			String csvStr = resSup.readEntity(String.class);
			assertTrue(csvStr.startsWith(CsvExportHelper.UTF8_BOM));
			assertTrue(csvStr.contains("AbsenceId,EmployeeId,EmployeeName,AbsenceTypeCode"));
			assertTrue(csvStr.contains("employee_emp"));
		}
	}

	@Test
	public void shouldExportReportsAsPdfViaQueryParamAcceptHeaderAndAlias() {
		String employeeToken = authenticate("employee", "admin");
		String supervisorToken = authenticate("supervisor", "admin");

		// 1. Month Report PDF via ?format=pdf
		try (Response res = target()
				.path("chronivaro/v1/reports/month")
				.queryParam("yearMonth", "2026-08")
				.queryParam("format", "pdf")
				.request()
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(200, res.getStatus());
			assertTrue(res.getMediaType().toString().startsWith("application/pdf"));
			assertTrue(res.getHeaderString("Content-Disposition").contains("month-report-employee_emp-2026-08.pdf"));
			byte[] pdfBytes = res.readEntity(byte[].class);
			assertNotNull(pdfBytes);
			assertTrue(pdfBytes.length > 500);
			assertTrue(new String(pdfBytes, 0, 5).startsWith("%PDF-"));
		}

		// 2. Month Report PDF via Accept header
		try (Response res = target()
				.path("chronivaro/v1/reports/month")
				.queryParam("yearMonth", "2026-08")
				.request("application/pdf")
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(200, res.getStatus());
			assertTrue(res.getMediaType().toString().startsWith("application/pdf"));
			byte[] pdfBytes = res.readEntity(byte[].class);
			assertTrue(new String(pdfBytes, 0, 5).startsWith("%PDF-"));
		}

		// 3. Month Report PDF via /month.pdf alias
		try (Response res = target()
				.path("chronivaro/v1/reports/month.pdf")
				.queryParam("yearMonth", "2026-08")
				.request()
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(200, res.getStatus());
			assertTrue(res.getMediaType().toString().startsWith("application/pdf"));
			byte[] pdfBytes = res.readEntity(byte[].class);
			assertTrue(new String(pdfBytes, 0, 5).startsWith("%PDF-"));
		}

		// 4. Vacation Report PDF via ?format=pdf
		try (Response res = target()
				.path("chronivaro/v1/reports/vacation")
				.queryParam("year", 2026)
				.queryParam("format", "pdf")
				.request()
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(200, res.getStatus());
			assertTrue(res.getMediaType().toString().startsWith("application/pdf"));
			assertTrue(res.getHeaderString("Content-Disposition").contains("vacation-report-employee_emp-2026.pdf"));
			byte[] pdfBytes = res.readEntity(byte[].class);
			assertTrue(new String(pdfBytes, 0, 5).startsWith("%PDF-"));
		}

		// 5. Vacation Report PDF via /vacation.pdf alias
		try (Response res = target()
				.path("chronivaro/v1/reports/vacation.pdf")
				.queryParam("year", 2026)
				.request()
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(200, res.getStatus());
			assertTrue(res.getMediaType().toString().startsWith("application/pdf"));
			byte[] pdfBytes = res.readEntity(byte[].class);
			assertTrue(new String(pdfBytes, 0, 5).startsWith("%PDF-"));
		}

		// 6. Absences Report PDF via ?format=pdf for supervisor
		try (Response res = target()
				.path("chronivaro/v1/reports/absences")
				.queryParam("teamId", "team-1")
				.queryParam("format", "pdf")
				.request()
				.header("Authorization", supervisorToken)
				.get()) {

			assertEquals(200, res.getStatus());
			assertTrue(res.getMediaType().toString().startsWith("application/pdf"));
			assertTrue(res.getHeaderString("Content-Disposition").contains("absence-report-team-1"));
			byte[] pdfBytes = res.readEntity(byte[].class);
			assertTrue(new String(pdfBytes, 0, 5).startsWith("%PDF-"));
		}

		// 7. Absences Report PDF via /absences.pdf alias
		try (Response res = target()
				.path("chronivaro/v1/reports/absences.pdf")
				.queryParam("from", "2026-08-01")
				.queryParam("to", "2026-08-31")
				.request()
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(200, res.getStatus());
			assertTrue(res.getMediaType().toString().startsWith("application/pdf"));
			byte[] pdfBytes = res.readEntity(byte[].class);
			assertTrue(new String(pdfBytes, 0, 5).startsWith("%PDF-"));
		}

		// 8. Absences Report PDF via ?format=pdf for employee without explicit employeeId
		try (Response res = target()
				.path("chronivaro/v1/reports/absences")
				.queryParam("from", "2026-08-01")
				.queryParam("to", "2026-08-21")
				.queryParam("format", "pdf")
				.queryParam("lang", "de")
				.request()
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(200, res.getStatus());
			assertTrue(res.getMediaType().toString().startsWith("application/pdf"));
			assertTrue(res.getHeaderString("Content-Disposition").contains("absence-report-employee_emp"));
			byte[] pdfBytes = res.readEntity(byte[].class);
			assertNotNull(pdfBytes);
			assertTrue(new String(pdfBytes, 0, 5).startsWith("%PDF-"));
		}

		// 9. On-Call Report PDF via ?format=pdf
		try (Response res = target()
				.path("chronivaro/v1/reports/on-call")
				.queryParam("from", "2026-08-01")
				.queryParam("to", "2026-08-31")
				.queryParam("format", "pdf")
				.queryParam("lang", "de")
				.request()
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(200, res.getStatus());
			assertTrue(res.getMediaType().toString().startsWith("application/pdf"));
			assertTrue(res.getHeaderString("Content-Disposition").contains("on-call-report"));
			byte[] pdfBytes = res.readEntity(byte[].class);
			assertNotNull(pdfBytes);
			assertTrue(new String(pdfBytes, 0, 5).startsWith("%PDF-"));
		}

		// 10. On-Call Report PDF via /on-call.pdf alias
		try (Response res = target()
				.path("chronivaro/v1/reports/on-call.pdf")
				.queryParam("from", "2026-08-01")
				.queryParam("to", "2026-08-31")
				.request()
				.header("Authorization", employeeToken)
				.get()) {

			assertEquals(200, res.getStatus());
			assertTrue(res.getMediaType().toString().startsWith("application/pdf"));
			byte[] pdfBytes = res.readEntity(byte[].class);
			assertNotNull(pdfBytes);
			assertTrue(new String(pdfBytes, 0, 5).startsWith("%PDF-"));
		}
	}
}
