package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.core.report.CsvExportHelper;
import ch.atexxi.chronivaro.core.service.CreditVacationEntitlementService;
import ch.atexxi.chronivaro.rest.dto.AbsenceReportDto;
import ch.atexxi.chronivaro.rest.dto.DaySummaryDto;
import ch.atexxi.chronivaro.rest.dto.MonthSummaryDto;
import ch.atexxi.chronivaro.rest.dto.TeamReportDto;
import ch.atexxi.chronivaro.rest.dto.VacationAccountSummaryDto;
import ch.atexxi.chronivaro.rest.resource.ChronivaroRestHelper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class WebReportsUiTest extends AbstractChronivaroRestfulTest {

	private static final String ZONE = "Europe/Zurich";
	private static final Gson gson = ChronivaroRestHelper.createGson();

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

			emp1.setRelation(PARAM_CURRENT_SCHEDULE, sched1);
			tx.add(sched1);
			tx.add(emp1);

			// Employee 2 (Team 2, user: test)
			Resource emp2 = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			emp2.setId("employee_test");
			emp2.setName("Test User");
			emp2.setString(PARAM_FIRSTNAME, "Test");
			emp2.setString(PARAM_LASTNAME, "User");
			emp2.setString(PARAM_TIMEZONE, ZONE);
			emp2.setDate(PARAM_JOIN_DATE, LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of(ZONE)));
			emp2.setBoolean(PARAM_ACTIVE, true);
			emp2.setString(PARAM_USERNAME, "test");
			emp2.setString(PARAM_USER_ID, "test");
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

			emp2.setRelation(PARAM_CURRENT_SCHEDULE, sched2);
			tx.add(sched2);
			tx.add(emp2);

			// Supervisor (Leader of Team 1, user: supervisor)
			Resource sup = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			sup.setId("employee_sup");
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

			sup.setRelation(PARAM_CURRENT_SCHEDULE, schedSup);
			team1.setRelation(PARAM_LEADER, sup);

			tx.add(schedSup);
			tx.add(sup);
			tx.update(team1);

			// Add a work entry for employee_emp on 2026-08-03
			Resource workDay = tx.getResourceTemplate(TYPE_WORK_DAY, true);
			workDay.setId("wd-employee_emp-2026-08-03");
			workDay.setRelationId(PARAM_EMPLOYEE, "employee_emp");
			workDay.setDate(PARAM_DATE, LocalDate.of(2026, 8, 3).atStartOfDay(ZoneId.of(ZONE)));

			Resource entry = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
			entry.setId("work-entry-1");
			entry.setName("Work Entry 1");
			entry.setRelation(PARAM_EMPLOYEE, emp1);
			entry.setRelation(PARAM_WORK_DAY, workDay);
			entry.setDate(PARAM_START, ZonedDateTime.parse("2026-08-03T08:00:00+02:00[Europe/Zurich]"));
			entry.setDate(PARAM_END, ZonedDateTime.parse("2026-08-03T17:00:00+02:00[Europe/Zurich]"));
			entry.setString(PARAM_WORKING_LOCATION, "OFFICE");
			entry.setString(PARAM_SOURCE, "MANUAL");
			entry.setString(PARAM_COMMENT, "Working on project");
			entry.setString(PARAM_CREATED_BY, "employee");
			tx.add(entry);

			workDay.addRelation(PARAM_WORK_ENTRIES, entry);
			tx.add(workDay);

			// Add an approved absence for employee_emp on 2026-08-10 to 2026-08-14
			Resource abs = tx.getResourceTemplate(TYPE_ABSENCE, true);
			abs.setId("abs-1");
			abs.setName("Absence 1");
			abs.setRelation(PARAM_EMPLOYEE, emp1);
			abs.setRelation(PARAM_ABSENCE_TYPE, vacationType);
			abs.setString(PARAM_ABSENCE_TYPE, "vacation");
			abs.setDate(PARAM_START, LocalDate.of(2026, 8, 10).atStartOfDay(ZoneId.of(ZONE)));
			abs.setDate(PARAM_END, LocalDate.of(2026, 8, 14).atTime(23, 59, 59).atZone(ZoneId.of(ZONE)));
			abs.setString(PARAM_DURATION_TYPE, DURATION_FULL_DAY);
			abs.setInteger(PARAM_MINUTES, 2400);
			abs.setString(PARAM_STATE, STATE_APPROVED);
			abs.setString(PARAM_COMMENT, "Summer vacation");
			abs.setDate(PARAM_SUBMITTED_AT, ZonedDateTime.now());
			abs.setDate(PARAM_APPROVED_AT, ZonedDateTime.now());
			abs.setString(PARAM_APPROVED_BY, "supervisor");
			tx.add(abs);

			// Add vacation account usage entry
			Resource vacUsage = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
			vacUsage.setId("vac-usage-1");
			vacUsage.setName("Vacation Usage 1");
			vacUsage.setRelation(PARAM_EMPLOYEE, emp1);
			vacUsage.setRelation(PARAM_ABSENCE, abs);
			vacUsage.setString(PARAM_VACATION_TYPE, VACATION_USAGE);
			vacUsage.setInteger(PARAM_VALUE, -2400);
			vacUsage.setDate(PARAM_DATE, LocalDate.of(2026, 8, 10).atStartOfDay(ZoneId.of(ZONE)));
			tx.add(vacUsage);

			tx.commitOnClose();
		}

		// Initial vacation credit for employee_emp
		CreditVacationEntitlementService.CreditVacationEntitlementArgument creditArg = new CreditVacationEntitlementService.CreditVacationEntitlementArgument();
		creditArg.employeeId = "employee_emp";
		creditArg.year = 2026;
		runtimeMock.getServiceHandler().doService(runtimeMock.loginAdmin(), new CreditVacationEntitlementService(), creditArg);
	}

	@Test
	public void shouldVerifyWebReportAssetsAndWiring() throws IOException {
		File webDir = new File("chronivaro-web/src/main/webapp");
		if (!webDir.exists()) {
			webDir = new File("../chronivaro-web/src/main/webapp");
		}
		assertTrue("Webapp directory must exist", webDir.exists());

		File indexHtml = new File(webDir, "index.html");
		assertTrue("index.html must exist", indexHtml.exists());
		String indexContent = Files.readString(indexHtml.toPath());
		assertTrue("index.html must contain Reports navigation link", indexContent.contains("href=\"#reports\"") || indexContent.contains("href='#reports'"));
		assertTrue("Reports nav link must be role-accessible", indexContent.contains("data-roles=\"Employee,Supervisor,HR,Administrator\"") || indexContent.contains("data-roles=\"Employee"));

		File appJs = new File(webDir, "js/app.js");
		assertTrue("app.js must exist", appJs.exists());
		String appContent = Files.readString(appJs.toPath());
		assertTrue("app.js must import ReportsView", appContent.contains("ReportsView"));
		assertTrue("app.js must route 'reports'", appContent.contains("case 'reports':"));

		File reportsViewJs = new File(webDir, "js/pages/ReportsView.js");
		assertTrue("ReportsView.js must exist", reportsViewJs.exists());
		String reportsViewContent = Files.readString(reportsViewJs.toPath());
		assertTrue("ReportsView must export default class", reportsViewContent.contains("export default class ReportsView"));
		assertTrue("ReportsView must contain generateReport method", reportsViewContent.contains("generateReport"));
		assertTrue("ReportsView must contain exportCsv method", reportsViewContent.contains("exportCsv"));
		assertTrue("ReportsView must contain exportPdf method", reportsViewContent.contains("exportPdf"));
		assertTrue("ReportsView must contain PDF export button", reportsViewContent.contains("btn-export-pdf"));
		assertTrue("ReportsView must render day report", reportsViewContent.contains("renderDayReport"));
		assertTrue("ReportsView must render month report", reportsViewContent.contains("renderMonthReport"));
		assertTrue("ReportsView must render vacation report", reportsViewContent.contains("renderVacationReport"));
		assertTrue("ReportsView must render team report", reportsViewContent.contains("renderTeamReport"));
		assertTrue("ReportsView must render absence report", reportsViewContent.contains("renderAbsenceReport"));

		File myPeriodsViewJs = new File(webDir, "js/pages/MyPeriodsView.js");
		assertTrue("MyPeriodsView.js must exist", myPeriodsViewJs.exists());
		String myPeriodsViewContent = Files.readString(myPeriodsViewJs.toPath());
		assertTrue("MyPeriodsView must contain downloadPdf method", myPeriodsViewContent.contains("downloadPdf"));
		assertTrue("MyPeriodsView must contain PDF download button", myPeriodsViewContent.contains("download-period-pdf-btn"));

		File reportApiJs = new File(webDir, "js/api/ReportApi.js");
		assertTrue("ReportApi.js must exist", reportApiJs.exists());
		String reportApiContent = Files.readString(reportApiJs.toPath());
		assertTrue("ReportApi must define getDayReport", reportApiContent.contains("getDayReport"));
		assertTrue("ReportApi must define downloadDayReportCsv", reportApiContent.contains("downloadDayReportCsv"));
		assertTrue("ReportApi must define getMonthReport", reportApiContent.contains("getMonthReport"));
		assertTrue("ReportApi must define downloadMonthReportCsv", reportApiContent.contains("downloadMonthReportCsv"));
		assertTrue("ReportApi must define downloadMonthReportPdf", reportApiContent.contains("downloadMonthReportPdf"));
		assertTrue("ReportApi must define getVacationReport", reportApiContent.contains("getVacationReport"));
		assertTrue("ReportApi must define downloadVacationReportCsv", reportApiContent.contains("downloadVacationReportCsv"));
		assertTrue("ReportApi must define downloadVacationReportPdf", reportApiContent.contains("downloadVacationReportPdf"));
		assertTrue("ReportApi must define getTeamReport", reportApiContent.contains("getTeamReport"));
		assertTrue("ReportApi must define downloadTeamReportCsv", reportApiContent.contains("downloadTeamReportCsv"));
		assertTrue("ReportApi must define getAbsenceReport", reportApiContent.contains("getAbsenceReport"));
		assertTrue("ReportApi must define downloadAbsenceReportCsv", reportApiContent.contains("downloadAbsenceReportCsv"));
		assertTrue("ReportApi must define downloadAbsenceReportPdf", reportApiContent.contains("downloadAbsenceReportPdf"));

		File styleCss = new File(webDir, "assets/css/style.css");
		assertTrue("style.css must exist", styleCss.exists());
		String cssContent = Files.readString(styleCss.toPath());
		assertTrue("style.css must include report styles", cssContent.contains("#reports-view") && cssContent.contains(".btn-export"));
	}

	@Test
	public void shouldExecuteDayReportFlowAsJsonAndCsv() {
		String employeeToken = authenticate("employee", "admin");

		// Get Day Report JSON
		try (Response jsonResp = target()
				.path("/chronivaro/v1/reports/day")
				.queryParam("date", "2026-08-03")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, employeeToken)
				.get()) {

			assertEquals(200, jsonResp.getStatus());
			String jsonStr = jsonResp.readEntity(String.class);
			JsonObject dayObj = JsonParser.parseString(jsonStr).getAsJsonObject();
			assertEquals("2026-08-03", dayObj.get("date").getAsString());
			assertEquals(480, dayObj.get("targetMinutes").getAsInt());
			assertEquals(540, dayObj.get("actualMinutes").getAsInt());
			assertEquals(60, dayObj.get("balance").getAsInt());
			assertTrue(dayObj.has("workEntries"));
		}

		// Get Day Report CSV with UTF-8 BOM
		try (Response csvResp = target()
				.path("/chronivaro/v1/reports/day")
				.queryParam("date", "2026-08-03")
				.queryParam("format", "csv")
				.request("text/csv")
				.header(HttpHeaders.AUTHORIZATION, employeeToken)
				.get()) {

			assertEquals(200, csvResp.getStatus());
			String csvStr = csvResp.readEntity(String.class);
			assertTrue("CSV must start with UTF-8 BOM", csvStr.startsWith(CsvExportHelper.UTF8_BOM));
			assertTrue(csvStr.contains("Date,EmployeeId,EmployeeName,TargetMinutes,ActualMinutes"));
			assertTrue(csvStr.contains("employee_emp"));
			assertTrue(csvStr.contains("work-entry-1"));
		}
	}

	@Test
	public void shouldExecuteMonthAndVacationReportsFlow() {
		String employeeToken = authenticate("employee", "admin");

		// Month Report JSON
		try (Response monthResp = target()
				.path("/chronivaro/v1/reports/month")
				.queryParam("yearMonth", "2026-08")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, employeeToken)
				.get()) {

			assertEquals(200, monthResp.getStatus());
			String monthStr = monthResp.readEntity(String.class);
			JsonObject monthObj = JsonParser.parseString(monthStr).getAsJsonObject();
			assertEquals("2026-08", monthObj.get("yearMonth").getAsString());
			assertTrue(monthObj.get("totalActualMinutes").getAsInt() >= 540);
			assertTrue(monthObj.get("totalAbsenceMinutes").getAsInt() >= 2400);
		}

		// Vacation Report JSON
		try (Response vacResp = target()
				.path("/chronivaro/v1/reports/vacation")
				.queryParam("year", "2026")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, employeeToken)
				.get()) {

			assertEquals(200, vacResp.getStatus());
			String vacStr = vacResp.readEntity(String.class);
			JsonObject vacObj = JsonParser.parseString(vacStr).getAsJsonObject();
			assertEquals(2026, vacObj.get("year").getAsInt());
			assertEquals(12000, vacObj.get("entitlementMinutes").getAsInt());
			assertEquals(2400, vacObj.get("usageMinutes").getAsInt());
			assertEquals(9600, vacObj.get("remainingMinutes").getAsInt());
		}
	}

	@Test
	public void shouldExecuteTeamAndAbsenceReportsFlowForSupervisor() {
		String supToken = authenticate("supervisor", "admin");

		// Team Report JSON
		try (Response teamResp = target()
				.path("/chronivaro/v1/reports/team")
				.queryParam("teamId", "team-1")
				.queryParam("yearMonth", "2026-08")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supToken)
				.get()) {

			assertEquals(200, teamResp.getStatus());
			String teamStr = teamResp.readEntity(String.class);
			JsonObject teamObj = JsonParser.parseString(teamStr).getAsJsonObject();
			assertEquals("team-1", teamObj.get("teamId").getAsString());
			assertEquals("Engineering", teamObj.get("teamName").getAsString());
			assertTrue(teamObj.getAsJsonArray("employees").size() > 0);
		}

		// Team Report CSV
		try (Response teamCsvResp = target()
				.path("/chronivaro/v1/reports/team")
				.queryParam("teamId", "team-1")
				.queryParam("yearMonth", "2026-08")
				.queryParam("format", "csv")
				.request("text/csv")
				.header(HttpHeaders.AUTHORIZATION, supToken)
				.get()) {

			assertEquals(200, teamCsvResp.getStatus());
			String teamCsv = teamCsvResp.readEntity(String.class);
			assertTrue(teamCsv.startsWith(CsvExportHelper.UTF8_BOM));
			assertTrue(teamCsv.contains("EmployeeId,EmployeeName,TeamId,YearMonth,TargetMinutes,ActualMinutes"));
			assertTrue(teamCsv.contains("employee_emp"));
		}

		// Absences Report JSON
		try (Response absResp = target()
				.path("/chronivaro/v1/reports/absences")
				.queryParam("from", "2026-08-01")
				.queryParam("to", "2026-08-31")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, supToken)
				.get()) {

			assertEquals(200, absResp.getStatus());
			String absStr = absResp.readEntity(String.class);
			JsonObject absObj = JsonParser.parseString(absStr).getAsJsonObject();
			assertTrue(absObj.getAsJsonArray("items").size() > 0);
			JsonObject first = absObj.getAsJsonArray("items").get(0).getAsJsonObject();
			assertEquals("employee_emp", first.get("employeeId").getAsString());
			assertEquals("APPROVED", first.get("state").getAsString());
			assertTrue(first.get("paid").getAsBoolean());
		}
	}

	@Test
	public void shouldEnforceScopingRestrictionsOnReports() {
		String empToken = authenticate("employee", "admin");

		// Employee attempting to access another employee's day report -> 403 Forbidden
		try (Response forbiddenDay = target()
				.path("/chronivaro/v1/reports/day")
				.queryParam("date", "2026-08-03")
				.queryParam("employeeId", "employee_test")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, empToken)
				.get()) {

			assertEquals(403, forbiddenDay.getStatus());
		}

		// Employee attempting to access team report -> 403 Forbidden
		try (Response forbiddenTeam = target()
				.path("/chronivaro/v1/reports/team")
				.queryParam("teamId", "team-1")
				.queryParam("yearMonth", "2026-08")
				.request(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, empToken)
				.get()) {

			assertEquals(403, forbiddenTeam.getStatus());
		}
	}

	@Test
	public void shouldDownloadMonthAndVacationReportsPdf() {
		String employeeToken = authenticate("employee", "admin");

		// 1. Month Report PDF download
		try (Response monthPdfResp = target()
				.path("/chronivaro/v1/reports/month")
				.queryParam("yearMonth", "2026-08")
				.queryParam("format", "pdf")
				.queryParam("lang", "de")
				.request("application/pdf")
				.header(HttpHeaders.AUTHORIZATION, employeeToken)
				.get()) {

			assertEquals(200, monthPdfResp.getStatus());
			assertTrue(monthPdfResp.getMediaType().toString().startsWith("application/pdf"));
			byte[] pdfBytes = monthPdfResp.readEntity(byte[].class);
			assertNotNull(pdfBytes);
			assertTrue(new String(pdfBytes, 0, 5).startsWith("%PDF-"));
		}

		// 2. Vacation Report PDF download
		try (Response vacPdfResp = target()
				.path("/chronivaro/v1/reports/vacation")
				.queryParam("year", "2026")
				.queryParam("format", "pdf")
				.queryParam("lang", "en")
				.request("application/pdf")
				.header(HttpHeaders.AUTHORIZATION, employeeToken)
				.get()) {

			assertEquals(200, vacPdfResp.getStatus());
			assertTrue(vacPdfResp.getMediaType().toString().startsWith("application/pdf"));
			byte[] pdfBytes = vacPdfResp.readEntity(byte[].class);
			assertNotNull(pdfBytes);
			assertTrue(new String(pdfBytes, 0, 5).startsWith("%PDF-"));
		}
	}
}
