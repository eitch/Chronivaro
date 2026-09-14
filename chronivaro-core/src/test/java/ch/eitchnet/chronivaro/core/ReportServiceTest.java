package ch.eitchnet.chronivaro.core;

import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.WorkEntryHelper;
import ch.eitchnet.chronivaro.core.report.AbsenceReportItem;
import ch.eitchnet.chronivaro.core.report.CsvExportHelper;
import ch.eitchnet.chronivaro.core.report.TeamReport;
import ch.eitchnet.chronivaro.core.service.AbsenceReportService;
import ch.eitchnet.chronivaro.core.service.TeamReportService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;

import static ch.eitchnet.chronivaro.core.ChronivaroTestHelper.createEmployee;
import static ch.eitchnet.chronivaro.core.ChronivaroTestHelper.createWorkEntry;
import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class ReportServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + ReportServiceTest.class.getSimpleName(),
				"src/test/resources");
		runtimeMock.startContainer();
		certificate = runtimeMock.login("admin", "admin");
	}

	@AfterClass
	public static void afterClass() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	@Test
	public void shouldGenerateTeamReportAndCalculateMissingBookings() {
		String teamId = "report-team-1";
		String emp1Id = "report-emp-1";
		String emp2Id = "report-emp-2";
		YearMonth yearMonth = YearMonth.of(2026, 8);

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			// Create Team
			Resource team = tx.getResourceTemplate(TYPE_TEAM, true);
			team.setId(teamId);
			team.setName("Engineering Team");
			team.setString(PARAM_NAME, "Engineering Team");
			tx.add(team);

			// Create Emp 1 (Has working entry on Monday 2026-08-03)
			Resource emp1 = createEmployee(tx, emp1Id, "Alice Engineer");
			emp1 = tx.readLock(emp1);
			emp1.setRelationId(PARAM_PRIMARY_TEAM, teamId);
			emp1.setString(PARAM_TIMEZONE, "Europe/Zurich");
			tx.update(emp1);

			Resource sched1 = tx.readLock(tx.getResourceByRelation(emp1, PARAM_CURRENT_SCHEDULE, true));
			sched1.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, 480);
			sched1.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, 480);
			tx.update(sched1);

			createWorkEntry(tx, emp1,
					ZonedDateTime.parse("2026-08-03T08:00:00+02:00[Europe/Zurich]"),
					ZonedDateTime.parse("2026-08-03T17:00:00+02:00[Europe/Zurich]"));

			// Create Emp 2 (No work entries, all target days are missing bookings)
			Resource emp2 = createEmployee(tx, emp2Id, "Bob Junior");
			emp2 = tx.readLock(emp2);
			emp2.setRelationId(PARAM_PRIMARY_TEAM, teamId);
			emp2.setString(PARAM_TIMEZONE, "Europe/Zurich");
			tx.update(emp2);

			Resource sched2 = tx.readLock(tx.getResourceByRelation(emp2, PARAM_CURRENT_SCHEDULE, true));
			sched2.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, 480);
			tx.update(sched2);

			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		TeamReportService.TeamReportArgument arg = new TeamReportService.TeamReportArgument();
		arg.teamId = teamId;
		arg.yearMonth = yearMonth;

		TeamReportService.TeamReportResult result = serviceHandler.doService(certificate, new TeamReportService(), arg);
		assertEquals(ServiceResult.success().getState(), result.getState());

		TeamReport report = result.teamReport;
		assertNotNull(report);
		assertEquals(teamId, report.teamId());
		assertEquals("Engineering Team", report.teamName());
		assertEquals(2, report.employeeSummaries().size());

		TeamReport.TeamEmployeeSummary emp1Sum = report.employeeSummaries().stream()
				.filter(e -> e.employeeId().equals(emp1Id)).findFirst().orElseThrow();
		assertTrue(emp1Sum.actualMinutes() > 0);
		// Emp1 worked on 1 Monday, but month has 5 Mondays and 4 Tuesdays -> 9 target days, 8 missing bookings
		assertEquals(8, emp1Sum.missingBookingsCount());

		TeamReport.TeamEmployeeSummary emp2Sum = report.employeeSummaries().stream()
				.filter(e -> e.employeeId().equals(emp2Id)).findFirst().orElseThrow();
		assertEquals(0, emp2Sum.actualMinutes());
		// Emp2 has 5 Mondays in Aug 2026 -> 5 missing bookings
		assertEquals(5, emp2Sum.missingBookingsCount());

		// Verify CSV Export
		String csv = CsvExportHelper.exportTeamReportToCsv(report);
		assertTrue("CSV should start with UTF-8 BOM", csv.startsWith(CsvExportHelper.UTF8_BOM));
		assertTrue("CSV should contain header", csv.contains("EmployeeId,EmployeeName,TeamId,YearMonth"));
		assertTrue("CSV should contain Alice", csv.contains("Alice Engineer"));
		assertTrue("CSV should contain Bob", csv.contains("Bob Junior"));
	}

	@Test
	public void shouldGenerateAbsenceReportAndExportCsv() {
		String empId = "report-emp-abs";
		LocalDate start = LocalDate.of(2026, 8, 10);
		LocalDate end = LocalDate.of(2026, 8, 14);

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource emp = createEmployee(tx, empId, "Charlie Vacationer");

			// Ensure absence type exists
			if (!tx.hasResource(TYPE_ABSENCE_TYPE, "VACATION")) {
				Resource absType = new Resource("VACATION", "Vacation", TYPE_ABSENCE_TYPE);
				absType.setString(PARAM_CODE, "VACATION");
				absType.setString(PARAM_NAME, "Annual Vacation");
				absType.setBoolean(PARAM_PAID, true);
				absType.setBoolean(PARAM_ACTIVE, true);
				tx.add(absType);
			}

			// Add Absence
			Resource absence = tx.getResourceTemplate(TYPE_ABSENCE, true);
			absence.setId("abs-report-1");
			absence.setName("Vacation Week");
			absence.setRelation(PARAM_EMPLOYEE, emp);
			absence.setRelationId(PARAM_ABSENCE_TYPE, "VACATION");
			absence.setDate(PARAM_START, start.atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(emp)));
			absence.setDate(PARAM_END, end.atTime(23, 59, 59).atZone(ChronivaroModelHelper.getEmployeeTimezone(emp)));
			absence.setString(PARAM_DURATION_TYPE, DURATION_FULL_DAY);
			absence.setInteger(PARAM_MINUTES, 2400);
			absence.setString(PARAM_STATE, STATE_APPROVED);
			absence.setString(PARAM_COMMENT, "Summer holidays, with comma and \"quotes\"");
			absence.setDate(PARAM_SUBMITTED_AT, ZonedDateTime.now());
			absence.setDate(PARAM_APPROVED_AT, ZonedDateTime.now());
			absence.setString(PARAM_APPROVED_BY, "supervisor_test");
			tx.add(absence);

			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		AbsenceReportService.AbsenceReportArgument arg = new AbsenceReportService.AbsenceReportArgument();
		arg.employeeId = empId;
		arg.from = LocalDate.of(2026, 8, 1);
		arg.to = LocalDate.of(2026, 8, 31);

		AbsenceReportService.AbsenceReportResult result = serviceHandler.doService(certificate, new AbsenceReportService(), arg);
		assertEquals(ServiceResult.success().getState(), result.getState());

		List<AbsenceReportItem> items = result.items;
		assertEquals(1, items.size());
		AbsenceReportItem item = items.getFirst();
		assertEquals(empId, item.employeeId());
		assertEquals("Charlie Vacationer", item.employeeName());
		assertEquals("VACATION", item.absenceTypeCode());
		assertEquals(STATE_APPROVED, item.state());
		assertTrue(item.paid());

		// Verify CSV Export and Escaping
		String csv = CsvExportHelper.exportAbsenceReportToCsv(items);
		assertTrue("CSV should start with UTF-8 BOM", csv.startsWith(CsvExportHelper.UTF8_BOM));
		assertTrue("CSV should contain header", csv.contains("AbsenceId,EmployeeId,EmployeeName,AbsenceTypeCode"));
		assertTrue("CSV should contain escaped comment", csv.contains("\"Summer holidays, with comma and \"\"quotes\"\"\""));
	}

	@Test
	public void shouldGenerateOnCallReportAndSummaries() {
		String empId = "report-emp-oncall";
		LocalDate start = LocalDate.of(2026, 8, 17);
		LocalDate end = LocalDate.of(2026, 8, 23);

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource emp = createEmployee(tx, empId, "David OnCall");

			// Create OnCallPeriod
			Resource period = tx.getResourceTemplate(TYPE_ON_CALL_PERIOD, true);
			period.setId("oncall-rep-1");
			period.setName("Weekly On-Call Shift");
			period.setRelation(PARAM_EMPLOYEE, emp);
			period.setDate(PARAM_START_DATE, start.atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(emp)));
			period.setDate(PARAM_END_DATE, end.atTime(23, 59, 59).atZone(ChronivaroModelHelper.getEmployeeTimezone(emp)));
			period.setString(PARAM_START_TIME, "17:00");
			period.setString(PARAM_END_TIME, "08:00");
			period.setString(PARAM_COMMENT, "Primary on-call emergency duty");
			period.setString(PARAM_CREATED_BY, "admin");
			tx.add(period);

			// Add On-Call Work Entry
			Resource workEntry = createWorkEntry(tx, emp,
					ZonedDateTime.parse("2026-08-18T22:00:00+02:00[Europe/Zurich]"),
					ZonedDateTime.parse("2026-08-18T23:30:00+02:00[Europe/Zurich]"));
			workEntry.setBoolean(PARAM_IS_ON_CALL, true);
			workEntry.setString(PARAM_COMMENT, "Server outage fix");
			tx.update(workEntry);

			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		ch.eitchnet.chronivaro.core.service.OnCallReportService.OnCallReportArgument arg =
				new ch.eitchnet.chronivaro.core.service.OnCallReportService.OnCallReportArgument();
		arg.employeeId = empId;
		arg.from = LocalDate.of(2026, 8, 1);
		arg.to = LocalDate.of(2026, 8, 31);

		ch.eitchnet.chronivaro.core.service.OnCallReportService.OnCallReportResult result =
				serviceHandler.doService(certificate, new ch.eitchnet.chronivaro.core.service.OnCallReportService(), arg);
		assertEquals(ServiceResult.success().getState(), result.getState());

		ch.eitchnet.chronivaro.core.report.OnCallReport report = result.report;
		assertNotNull(report);
		assertEquals(1, report.totalPeriodsCount());
		assertEquals(1, report.totalWorkEntriesCount());
		assertEquals(90, report.totalWorkEntryMinutes()); // 22:00 to 23:30 = 1.5h = 90m

		// Test MonthSummary on-call calculation
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			ch.eitchnet.chronivaro.core.model.MonthSummary monthSummary =
					ch.eitchnet.chronivaro.core.service.MonthSummaryService.calculateMonthSummary(tx, empId, YearMonth.of(2026, 8));
			assertTrue("Total on call minutes in month should be 90", monthSummary.totalOnCallMinutes() == 90);
		}

		// Verify CSV export
		String csv = CsvExportHelper.exportOnCallReportToCsv(report);
		assertTrue("CSV should start with UTF-8 BOM", csv.startsWith(CsvExportHelper.UTF8_BOM));
		assertTrue("CSV should contain OnCallPeriods section", csv.contains("OnCallPeriods:"));
		assertTrue("CSV should contain OnCallWorkEntries section", csv.contains("OnCallWorkEntries:"));
		assertTrue("CSV should contain employee name", csv.contains("David OnCall"));
		assertTrue("CSV should contain duration 90", csv.contains("90,01:30"));

		// Verify PDF export
		Resource companyConfig;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			companyConfig = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", false);
		}
		byte[] pdf = ch.eitchnet.chronivaro.core.report.PdfExportHelper.exportOnCallReportToPdf(report, companyConfig, "de");
		assertNotNull(pdf);
		assertTrue("PDF byte array should not be empty", pdf.length > 0);
	}

	@Test
	public void shouldFormatDurationAndEscapeCsvCorrectly() {
		assertEquals("00:00", CsvExportHelper.formatDuration(0));
		assertEquals("08:00", CsvExportHelper.formatDuration(480));
		assertEquals("01:30", CsvExportHelper.formatDuration(90));
		assertEquals("-02:15", CsvExportHelper.formatDuration(-135));

		assertEquals("", CsvExportHelper.escapeCsv(null));
		assertEquals("SimpleText", CsvExportHelper.escapeCsv("SimpleText"));
		assertEquals("\"Hello, World\"", CsvExportHelper.escapeCsv("Hello, World"));
		assertEquals("\"Hello \"\"World\"\"\"", CsvExportHelper.escapeCsv("Hello \"World\""));
		assertEquals("\"Line1\nLine2\"", CsvExportHelper.escapeCsv("Line1\nLine2"));
	}

	@Test
	public void shouldMaskTeammateAbsencesForRegularEmployeeExceptVacation() {
		String teamId = "report-team-masking";
		String emp1Id = "report-emp-caller";
		String emp2Id = "report-emp-teammate";
		String emp3Id = "report-emp-otherteam";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			// Ensure illness type exists
			if (!tx.hasResource(TYPE_ABSENCE_TYPE, "ILLNESS")) {
				Resource illnessType = new Resource("ILLNESS", "Illness", TYPE_ABSENCE_TYPE);
				illnessType.setString(PARAM_CODE, "ILLNESS");
				illnessType.setString(PARAM_NAME, "Illness");
				illnessType.setBoolean(PARAM_PAID, true);
				illnessType.setBoolean(PARAM_ACTIVE, true);
				tx.add(illnessType);
			}

			// Create Team 1
			Resource team1 = tx.getResourceTemplate(TYPE_TEAM, true);
			team1.setId(teamId);
			team1.setName("Masking Team");
			team1.setString(PARAM_NAME, "Masking Team");
			tx.add(team1);

			// Create Team 2
			Resource team2 = tx.getResourceTemplate(TYPE_TEAM, true);
			team2.setId("report-other-team");
			team2.setName("Other Team");
			team2.setString(PARAM_NAME, "Other Team");
			tx.add(team2);

			// Create caller employee (in team 1)
			Resource emp1 = createEmployee(tx, emp1Id, "Caller User");
			emp1 = tx.readLock(emp1);
			emp1.setRelationId(PARAM_PRIMARY_TEAM, teamId);
			emp1.setString(PARAM_USERNAME, "employee");
			emp1.setString(PARAM_USER_ID, "employee");
			tx.update(emp1);

			// Create teammate employee (in team 1)
			Resource emp2 = createEmployee(tx, emp2Id, "Teammate User");
			emp2 = tx.readLock(emp2);
			emp2.setRelationId(PARAM_PRIMARY_TEAM, teamId);
			tx.update(emp2);

			// Create other employee (in team 2)
			Resource emp3 = createEmployee(tx, emp3Id, "Other Team User");
			emp3 = tx.readLock(emp3);
			emp3.setRelationId(PARAM_PRIMARY_TEAM, "report-other-team");
			tx.update(emp3);

			// Add Illness absence for caller
			Resource abs1 = tx.getResourceTemplate(TYPE_ABSENCE, true);
			abs1.setId("abs-caller-ill");
			abs1.setName("Caller Illness");
			abs1.setRelation(PARAM_EMPLOYEE, emp1);
			abs1.setRelationId(PARAM_ABSENCE_TYPE, "ILLNESS");
			abs1.setDate(PARAM_START, LocalDate.of(2026, 8, 3).atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(emp1)));
			abs1.setDate(PARAM_END, LocalDate.of(2026, 8, 3).atTime(23, 59, 59).atZone(ChronivaroModelHelper.getEmployeeTimezone(emp1)));
			abs1.setString(PARAM_DURATION_TYPE, DURATION_FULL_DAY);
			abs1.setInteger(PARAM_MINUTES, 480);
			abs1.setString(PARAM_STATE, STATE_APPROVED);
			abs1.setString(PARAM_COMMENT, "Caller secret illness comment");
			tx.add(abs1);

			// Add Vacation absence for teammate
			Resource abs2 = tx.getResourceTemplate(TYPE_ABSENCE, true);
			abs2.setId("abs-teammate-vac");
			abs2.setName("Teammate Vacation");
			abs2.setRelation(PARAM_EMPLOYEE, emp2);
			abs2.setRelationId(PARAM_ABSENCE_TYPE, "VACATION");
			abs2.setDate(PARAM_START, LocalDate.of(2026, 8, 4).atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(emp2)));
			abs2.setDate(PARAM_END, LocalDate.of(2026, 8, 4).atTime(23, 59, 59).atZone(ChronivaroModelHelper.getEmployeeTimezone(emp2)));
			abs2.setString(PARAM_DURATION_TYPE, DURATION_FULL_DAY);
			abs2.setInteger(PARAM_MINUTES, 480);
			abs2.setString(PARAM_STATE, STATE_APPROVED);
			abs2.setString(PARAM_COMMENT, "Teammate vacation comment");
			tx.add(abs2);

			// Add Illness absence for teammate
			Resource abs3 = tx.getResourceTemplate(TYPE_ABSENCE, true);
			abs3.setId("abs-teammate-ill");
			abs3.setName("Teammate Illness");
			abs3.setRelation(PARAM_EMPLOYEE, emp2);
			abs3.setRelationId(PARAM_ABSENCE_TYPE, "ILLNESS");
			abs3.setDate(PARAM_START, LocalDate.of(2026, 8, 5).atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(emp2)));
			abs3.setDate(PARAM_END, LocalDate.of(2026, 8, 5).atTime(23, 59, 59).atZone(ChronivaroModelHelper.getEmployeeTimezone(emp2)));
			abs3.setString(PARAM_DURATION_TYPE, DURATION_FULL_DAY);
			abs3.setInteger(PARAM_MINUTES, 480);
			abs3.setString(PARAM_STATE, STATE_APPROVED);
			abs3.setString(PARAM_COMMENT, "Teammate confidential doctor note");
			tx.add(abs3);

			// Add Vacation absence for other team employee
			Resource abs4 = tx.getResourceTemplate(TYPE_ABSENCE, true);
			abs4.setId("abs-other-vac");
			abs4.setName("Other Vacation");
			abs4.setRelation(PARAM_EMPLOYEE, emp3);
			abs4.setRelationId(PARAM_ABSENCE_TYPE, "VACATION");
			abs4.setDate(PARAM_START, LocalDate.of(2026, 8, 6).atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(emp3)));
			abs4.setDate(PARAM_END, LocalDate.of(2026, 8, 6).atTime(23, 59, 59).atZone(ChronivaroModelHelper.getEmployeeTimezone(emp3)));
			abs4.setString(PARAM_DURATION_TYPE, DURATION_FULL_DAY);
			abs4.setInteger(PARAM_MINUTES, 480);
			abs4.setString(PARAM_STATE, STATE_APPROVED);
			tx.add(abs4);

			tx.commitOnClose();
		}

		Certificate callerCert = runtimeMock.login("employee", "admin");
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// 1. Caller queries without employeeId/teamId -> gets team absences (caller + teammate, NOT other team)
		AbsenceReportService.AbsenceReportArgument argAll = new AbsenceReportService.AbsenceReportArgument();
		argAll.from = LocalDate.of(2026, 8, 1);
		argAll.to = LocalDate.of(2026, 8, 31);
		AbsenceReportService.AbsenceReportResult resAll = serviceHandler.doService(callerCert, new AbsenceReportService(), argAll);
		assertEquals(ServiceResult.success().getState(), resAll.getState());

		List<AbsenceReportItem> items = resAll.items;
		assertEquals(3, items.size());

		// Caller's own illness: full type + comment visible
		AbsenceReportItem callerItem = items.stream().filter(i -> i.id().equals("abs-caller-ill")).findFirst().orElseThrow();
		assertEquals("ILLNESS", callerItem.absenceTypeCode());
		assertEquals("Caller secret illness comment", callerItem.comment());

		// Teammate's vacation: vacation type visible, comment masked
		AbsenceReportItem teamVacItem = items.stream().filter(i -> i.id().equals("abs-teammate-vac")).findFirst().orElseThrow();
		assertEquals("VACATION", teamVacItem.absenceTypeCode());
		assertEquals("", teamVacItem.comment());

		// Teammate's illness: masked to ABSENT / Abwesend, comment masked
		AbsenceReportItem teamIllItem = items.stream().filter(i -> i.id().equals("abs-teammate-ill")).findFirst().orElseThrow();
		assertEquals("ABSENT", teamIllItem.absenceTypeCode());
		assertEquals("Abwesend", teamIllItem.absenceTypeName());
		assertEquals("", teamIllItem.comment());

		// 2. Caller queries other team's employee -> AccessDenied
		AbsenceReportService.AbsenceReportArgument argOtherEmp = new AbsenceReportService.AbsenceReportArgument();
		argOtherEmp.employeeId = emp3Id;
		AbsenceReportService.AbsenceReportResult resOtherEmp = serviceHandler.doService(callerCert, new AbsenceReportService(), argOtherEmp);
		assertTrue(resOtherEmp.isNok());

		// 3. Caller queries other team -> AccessDenied
		AbsenceReportService.AbsenceReportArgument argOtherTeam = new AbsenceReportService.AbsenceReportArgument();
		argOtherTeam.teamId = "report-other-team";
		AbsenceReportService.AbsenceReportResult resOtherTeam = serviceHandler.doService(callerCert, new AbsenceReportService(), argOtherTeam);
		assertTrue(resOtherTeam.isNok());
	}
}
