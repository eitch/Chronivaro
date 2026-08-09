package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.MonthSummary;
import ch.atexxi.chronivaro.core.service.CreateEmployeeService;
import ch.atexxi.chronivaro.core.service.MonthSummaryService;
import li.strolch.model.ParameterBag;
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
import java.util.UUID;

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

		String employeeId = "extensive-emp";
		String locationId = "extensive-loc";
		String holidayCalendarId = "extensive-cal";
		String teamId = "extensive-team";

		// 1. Create Infrastructure
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			// Location
			Resource location = new Resource(locationId, "Extensive Location", TYPE_LOCATION);
			location.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			location.setString(BAG_RELATIONS, TYPE_HOLIDAY_CALENDAR, holidayCalendarId);
			tx.add(location);

			// Team
			Resource team = new Resource(teamId, "Extensive Team", TYPE_TEAM);
			tx.add(team);

			// Holiday
			Resource holiday = new Resource("may-day", "May Day", TYPE_HOLIDAY);
			holiday.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			holiday.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			holiday.setString(BAG_RELATIONS, TYPE_HOLIDAY_CALENDAR, holidayCalendarId);
			holiday.setDate(PARAM_DATE, ZonedDateTime.parse("2026-05-01T00:00:00+02:00[Europe/Zurich]"));
			holiday.setDouble(PARAM_CREDIT_FACTOR, 1.0);
			tx.add(holiday);

			tx.commitOnClose();
		}

		// 2. Add Employee
		CreateEmployeeService.EmployeeArgument createArg = new CreateEmployeeService.EmployeeArgument();
		createArg.id = employeeId;
		createArg.personalNumber = "EXT-001";
		createArg.displayName = "Extensive Employee";
		createArg.teamId = teamId;
		createArg.locationId = locationId;
		createArg.timezone = "Europe/Zurich";
		createArg.joinDate = LocalDate.of(2026, 1, 1);
		createArg.active = true;
		createArg.userId = "extuser";

		assertTrue(serviceHandler.doService(certificate, new CreateEmployeeService(), createArg).isOk());

		// 3. Configure Schedule
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource schedule = new Resource(UUID.randomUUID().toString(), "Extensive Schedule",
					TYPE_EMPLOYMENT_SCHEDULE_VERSION);
			schedule.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			schedule.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			schedule.setString(BAG_RELATIONS, TYPE_EMPLOYEE, employeeId);
			schedule.setDate(PARAM_VALID_FROM, ZonedDateTime.parse("2026-01-01T00:00:00+01:00[Europe/Zurich]"));
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, 480);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, 0);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, 0);
			tx.add(schedule);
			tx.commitOnClose();
		}

		// 4. Add Work Items (WorkEntries)
		// 2026-05-01: Holiday (Friday) - should not have work entries for normal test, but we can add some to see it works
		// 2026-05-04: Monday, 08:00 - 12:00, 13:00 - 17:00 (8h)
		// 2026-05-05: Tuesday, 08:30 - 12:30, 13:30 - 17:30 (8h)
		// 2026-05-06: Wednesday, 08:00 - 12:00 (4h) + Absence in afternoon
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			// Monday
			addWorkEntry(tx, employeeId, "2026-05-04T08:00:00+02:00[Europe/Zurich]", "2026-05-04T12:00:00+02:00[Europe/Zurich]");
			addWorkEntry(tx, employeeId, "2026-05-04T13:00:00+02:00[Europe/Zurich]", "2026-05-04T17:00:00+02:00[Europe/Zurich]");

			// Tuesday
			addWorkEntry(tx, employeeId, "2026-05-05T08:30:00+02:00[Europe/Zurich]", "2026-05-05T12:30:00+02:00[Europe/Zurich]");
			addWorkEntry(tx, employeeId, "2026-05-05T13:30:00+02:00[Europe/Zurich]", "2026-05-05T17:30:00+02:00[Europe/Zurich]");

			// Wednesday
			addWorkEntry(tx, employeeId, "2026-05-06T08:00:00+02:00[Europe/Zurich]", "2026-05-06T12:00:00+02:00[Europe/Zurich]");

			tx.commitOnClose();
		}

		// 5. Add Absence
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource absence = new Resource(UUID.randomUUID().toString(), "Vacation", TYPE_ABSENCE);
			absence.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			absence.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			absence.setString(BAG_RELATIONS, TYPE_EMPLOYEE, employeeId);
			absence.setDate(PARAM_START, ZonedDateTime.parse("2026-05-06T00:00:00+02:00[Europe/Zurich]"));
			absence.setDate(PARAM_END, ZonedDateTime.parse("2026-05-06T23:59:59+02:00[Europe/Zurich]"));
			absence.setString(PARAM_DURATION_TYPE, DURATION_HOURS);
			absence.setInteger(PARAM_MINUTES, 240);
			absence.setString(PARAM_STATE, STATE_APPROVED);
			tx.add(absence);
			tx.commitOnClose();
		}

		// 6. Verify Month Summary
		MonthSummaryService.MonthSummaryArgument arg = new MonthSummaryService.MonthSummaryArgument();
		arg.employeeId = employeeId;
		arg.yearMonth = YearMonth.of(2026, 5);

		MonthSummaryService.MonthSummaryResult result = serviceHandler.doService(certificate, new MonthSummaryService(), arg);
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

	private void addWorkEntry(StrolchTransaction tx, String employeeId, String start, String end) {
		Resource e = new Resource(UUID.randomUUID().toString(), "WorkEntry", TYPE_WORK_ENTRY);
		e.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
		e.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
		e.setString(BAG_RELATIONS, TYPE_EMPLOYEE, employeeId);
		e.setDate(PARAM_START, ZonedDateTime.parse(start));
		e.setDate(PARAM_END, ZonedDateTime.parse(end));
		tx.add(e);
	}
}
