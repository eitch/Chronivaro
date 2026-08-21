package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.MonthSummary;
import ch.atexxi.chronivaro.core.service.*;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.api.ServiceHandler;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExtensiveIntegrationTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + ExtensiveIntegrationTest.class.getSimpleName(),
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
	public void shouldPerformExtensiveApplicationTest() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// 1. Create Infrastructure
		CreateHolidayCalendarService.HolidayCalendarArgument calArg
				= new CreateHolidayCalendarService.HolidayCalendarArgument();
		calArg.name = "Extensive Calendar";
		assertTrue(serviceHandler.doService(certificate, new CreateHolidayCalendarService(), calArg).isOk());

		String holidayCalendarId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			holidayCalendarId = tx
					.streamResources(TYPE_HOLIDAY_CALENDAR)
					.filter(r -> r.getName().equals("Extensive Calendar"))
					.findFirst()
					.orElseThrow()
					.getId();
		}

		CreateLocationService.LocationArgument locArg = new CreateLocationService.LocationArgument();
		locArg.name = "Extensive Location";
		locArg.timezone = "Europe/Zurich";
		locArg.holidayCalendarId = holidayCalendarId;
		assertTrue(serviceHandler.doService(certificate, new CreateLocationService(), locArg).isOk());

		String locationId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			locationId = tx
					.streamResources(TYPE_LOCATION)
					.filter(r -> r.getName().equals("Extensive Location"))
					.findFirst()
					.orElseThrow()
					.getId();
		}

		CreateTeamService.TeamArgument teamArg = new CreateTeamService.TeamArgument();
		teamArg.name = "Extensive Team";
		assertTrue(serviceHandler.doService(certificate, new CreateTeamService(), teamArg).isOk());

		String teamId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			teamId = tx
					.streamResources(TYPE_TEAM)
					.filter(r -> r.getName().equals("Extensive Team"))
					.findFirst()
					.orElseThrow()
					.getId();
		}

		CreateHolidayService.HolidayArgument holidayArg = new CreateHolidayService.HolidayArgument();
		holidayArg.name = "May Day";
		holidayArg.holidayCalendarId = holidayCalendarId;
		holidayArg.date = LocalDate.of(2026, 5, 1);
		holidayArg.creditFactor = 1.0;
		assertTrue(serviceHandler.doService(certificate, new CreateHolidayService(), holidayArg).isOk());

		// 2. Add Employee
		CreateEmployeeService.EmployeeArgument createArg = new CreateEmployeeService.EmployeeArgument();
		createArg.personalNumber = "EXT-001";
		createArg.firstname = "Extensive";
		createArg.lastname = "Employee";
		createArg.birthdate = LocalDate.of(1990, 5, 20);
		createArg.teamId = teamId;
		createArg.locationId = locationId;
		createArg.timezone = "Europe/Zurich";
		createArg.joinDate = LocalDate.of(2026, 1, 1);
		createArg.username = "extensiveuser";
		createArg.active = true;

		assertTrue(serviceHandler.doService(certificate, new CreateEmployeeService(), createArg).isOk());

		String employeeId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			employeeId = tx
					.streamResources(TYPE_EMPLOYEE)
					.filter(r -> r.getString(PARAM_PERSONAL_NUMBER).equals("EXT-001"))
					.findFirst()
					.orElseThrow()
					.getId();
		}

		// 3. Configure Schedule
		CreateScheduleService.CreateScheduleArgument scheduleArg = new CreateScheduleService.CreateScheduleArgument();
		scheduleArg.employeeId = employeeId;
		scheduleArg.validFrom = ZonedDateTime.parse("2026-01-01T00:00:00+01:00[Europe/Zurich]");
		scheduleArg.monday = 480;
		scheduleArg.tuesday = 480;
		scheduleArg.wednesday = 480;
		scheduleArg.thursday = 480;
		scheduleArg.friday = 480;
		scheduleArg.saturday = 0;
		scheduleArg.sunday = 0;
		assertTrue(serviceHandler.doService(certificate, new CreateScheduleService(), scheduleArg).isOk());
		String scheduleId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			assertTrue("Employee should have currentSchedule relation", employee.hasRelation(PARAM_CURRENT_SCHEDULE));
			scheduleId = employee.getRelationId(PARAM_CURRENT_SCHEDULE);
		}

		// 4. Add Work Items (WorkEntries)
		// 2026-05-01: Holiday (Friday) - should not have work entries for normal test, but we can add some to see it works
		// 2026-05-04: Monday, 08:00 - 12:00, 13:00 - 17:00 (8h)
		// 2026-05-05: Tuesday, 08:30 - 12:30, 13:30 - 17:30 (8h)
		// 2026-05-06: Wednesday, 08:00 - 12:00 (4h) + Absence in afternoon
		addWorkEntry(serviceHandler, employeeId, "2026-05-04T08:00:00+02:00[Europe/Zurich]",
				"2026-05-04T12:00:00+02:00[Europe/Zurich]");
		addWorkEntry(serviceHandler, employeeId, "2026-05-04T13:00:00+02:00[Europe/Zurich]",
				"2026-05-04T17:00:00+02:00[Europe/Zurich]");
		addWorkEntry(serviceHandler, employeeId, "2026-05-05T08:30:00+02:00[Europe/Zurich]",
				"2026-05-05T12:30:00+02:00[Europe/Zurich]");
		addWorkEntry(serviceHandler, employeeId, "2026-05-05T13:30:00+02:00[Europe/Zurich]",
				"2026-05-05T17:30:00+02:00[Europe/Zurich]");
		addWorkEntry(serviceHandler, employeeId, "2026-05-06T08:00:00+02:00[Europe/Zurich]",
				"2026-05-06T12:00:00+02:00[Europe/Zurich]");

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			List<Resource> entries = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.toList();
			for (Resource entry : entries) {
				assertTrue("WorkEntry " + entry.getId() + " should have schedule relation", entry.hasRelation(PARAM_SCHEDULE));
				assertEquals(scheduleId, entry.getRelationId(PARAM_SCHEDULE));
			}
		}

		// 5. Add Absence
		// Credit vacation entitlement for employee
		CreditVacationEntitlementService.CreditVacationEntitlementArgument creditArg =
				new CreditVacationEntitlementService.CreditVacationEntitlementArgument(employeeId, 2026, false);
		assertTrue(serviceHandler.doService(certificate, new CreditVacationEntitlementService(), creditArg).isOk());

		RequestAbsenceService.RequestAbsenceArgument absenceArg = new RequestAbsenceService.RequestAbsenceArgument();
		absenceArg.employeeId = employeeId;
		absenceArg.absenceTypeCode = "VACATION";
		absenceArg.start = ZonedDateTime.parse("2026-05-06T00:00:00+02:00[Europe/Zurich]");
		absenceArg.end = ZonedDateTime.parse("2026-05-06T23:59:59+02:00[Europe/Zurich]");
		absenceArg.durationType = DURATION_HALF_DAY;
		absenceArg.dayPart = DAY_PART_AFTERNOON;
		li.strolch.service.api.ServiceResult absenceResult = serviceHandler.doService(certificate,
				new RequestAbsenceService(), absenceArg);
		assertTrue(absenceResult.getMessage(), absenceResult.isOk());

		String id;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			id = tx
					.streamResources(TYPE_ABSENCE)
					.filter(a -> a.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.findFirst()
					.orElseThrow()
					.getId();
		}
		assertTrue(serviceHandler
				.doService(certificate, new ApproveAbsenceService(), new li.strolch.service.StringArgument(id))
				.isOk());

		// 6. Verify Month Summary
		MonthSummaryService.MonthSummaryArgument arg = new MonthSummaryService.MonthSummaryArgument();
		arg.employeeId = employeeId;
		arg.yearMonth = YearMonth.of(2026, 5);

		MonthSummaryService.MonthSummaryResult result = serviceHandler.doService(certificate, new MonthSummaryService(),
				arg);
		assertTrue(result.getMessage(), result.isOk());

		MonthSummary summary = result.monthSummary;

		// 2026-05 has 21 working days (Mon-Fri)
		// 1st is Holiday (Friday)
		// 4th, 5th, 6th, 7th, 8th (Mon-Fri)
		// 11th-15th, 18th-22nd, 25th-29th
		// Total target = 21 * 480 = 10080 minutes
		assertEquals(10080, summary.totalTargetMinutes());

		// Actual:
		// 4th: 480
		// 5th: 480
		// 6th: 240
		// Total actual = 1200 minutes
		assertEquals(1200, summary.totalActualMinutes());

		// Holiday:
		// 1st: 480 minutes
		assertEquals(480, summary.totalHolidayMinutes());

		// Absence:
		// 6th: 240 minutes
		assertEquals(240, summary.totalAbsenceMinutes());

		// Balance = Actual + Holiday + Absence - Target
		// Balance = 1200 + 480 + 240 - 10080 = 1920 - 10080 = -8160
		assertEquals(-8160, summary.getPeriodBalance());
	}

	private void addWorkEntry(ServiceHandler serviceHandler, String employeeId, String start, String end) {
		AddWorkEntryService.AddWorkEntryArgument arg = new AddWorkEntryService.AddWorkEntryArgument();
		arg.employeeId = employeeId;
		arg.start = ZonedDateTime.parse(start);
		arg.end = ZonedDateTime.parse(end);
		assertTrue(serviceHandler.doService(certificate, new AddWorkEntryService(), arg).isOk());
	}
}
