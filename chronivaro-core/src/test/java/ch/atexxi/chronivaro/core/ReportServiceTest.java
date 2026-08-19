package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.report.AbsenceReportItem;
import ch.atexxi.chronivaro.core.report.CsvExportHelper;
import ch.atexxi.chronivaro.core.report.TeamReport;
import ch.atexxi.chronivaro.core.service.AbsenceReportService;
import ch.atexxi.chronivaro.core.service.TeamReportService;
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

import static ch.atexxi.chronivaro.core.ChronivaroTestHelper.createEmployee;
import static ch.atexxi.chronivaro.core.ChronivaroTestHelper.createWorkEntry;
import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
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
			absence.setString(PARAM_ABSENCE_TYPE, "VACATION");
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
}
