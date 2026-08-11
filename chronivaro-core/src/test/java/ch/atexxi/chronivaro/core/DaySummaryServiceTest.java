package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.DayState;
import ch.atexxi.chronivaro.core.model.DaySummary;
import ch.atexxi.chronivaro.core.service.DaySummaryService;
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
import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.assertEquals;

public class DaySummaryServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + DaySummaryServiceTest.class.getSimpleName(),
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
	public void shouldCalculateDaySummary() {
		String employeeId = "emp3";
		LocalDate date = LocalDate.of(2026, 2, 2); // Monday

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setId(employeeId);
			employee.setName("Jack Doe");
			employee.setBoolean(PARAM_ACTIVE, true);
			employee.setDate(PARAM_JOIN_DATE, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			employee.setString(PARAM_TIMEZONE, "Europe/Zurich");
			tx.add(employee);

			Resource schedule = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE_VERSION, true);
			schedule.setName("Schedule");
			schedule.setRelation(PARAM_EMPLOYEE, employee);
			schedule.setDate(PARAM_VALID_FROM, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Monday", 480);
			tx.add(schedule);

			// Work Entry 1: 08:00 - 12:00
			Resource e1 = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
			e1.setName("E1");
			e1.setRelation(PARAM_EMPLOYEE, employee);
			e1.setDate(PARAM_START, ZonedDateTime.parse("2026-02-02T08:00:00+01:00[Europe/Zurich]"));
			e1.setDate(PARAM_END, ZonedDateTime.parse("2026-02-02T12:00:00+01:00[Europe/Zurich]"));
			tx.add(e1);

			// Work Entry 2: 13:00 - 17:00
			Resource e2 = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
			e2.setName("E2");
			e2.setRelation(PARAM_EMPLOYEE, employee);
			e2.setDate(PARAM_START, ZonedDateTime.parse("2026-02-02T13:00:00+01:00[Europe/Zurich]"));
			e2.setDate(PARAM_END, ZonedDateTime.parse("2026-02-02T17:00:00+01:00[Europe/Zurich]"));
			tx.add(e2);

			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		DaySummaryService.DaySummaryArgument arg = new DaySummaryService.DaySummaryArgument();
		arg.employeeId = employeeId;
		arg.date = date;

		DaySummaryService.DaySummaryResult result = serviceHandler.doService(certificate, new DaySummaryService(), arg);
		assertEquals(ServiceResult.success().getState(), result.getState());

		DaySummary summary = result.daySummary;
		assertEquals(DayState.NOT_WORKING, summary.state());
		assertEquals(480, summary.targetMinutes());
		assertEquals(480, summary.actualMinutes());
		assertEquals(0, summary.holidayMinutes());
		assertEquals(0, summary.absenceMinutes());
		assertEquals(0, summary.getBalance());
		assertEquals(2, summary.workEntries().size());
		assertEquals(1, summary.breaks().size());
		assertEquals(60, summary.breaks().getFirst().durationMinutes());
	}

	@Test
	public void shouldCalculateDaySummaryWithActiveEntry() {
		String employeeId = "emp4";
		LocalDate date = LocalDate.now(); // Today

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setId(employeeId);
			employee.setName("Jane Doe");
			employee.setBoolean(PARAM_ACTIVE, true);
			employee.setDate(PARAM_JOIN_DATE, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			employee.setString(PARAM_TIMEZONE, "Europe/Zurich");
			tx.add(employee);

			Resource schedule = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE_VERSION, true);
			schedule.setName("Schedule");
			schedule.setRelation(PARAM_EMPLOYEE, employee);
			schedule.setDate(PARAM_VALID_FROM, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + date.getDayOfWeek().name().substring(0, 1).toUpperCase()
					+ date.getDayOfWeek().name().substring(1).toLowerCase(), 480);
			tx.add(schedule);

			// Active Work Entry: started 10 minutes ago
			ZonedDateTime start = ZonedDateTime.now(ChronivaroModelHelper.getEmployeeTimezone(employee)).minusMinutes(10);
			Resource e1 = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
			e1.setName("E1");
			e1.setRelation(PARAM_EMPLOYEE, employee);
			e1.setDate(PARAM_START, start);
			e1.setDate(PARAM_END, ZonedDateTime.parse("1970-01-01T00:00:00+01:00"));
			tx.add(e1);

			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		DaySummaryService.DaySummaryArgument arg = new DaySummaryService.DaySummaryArgument();
		arg.employeeId = employeeId;
		arg.date = date;

		DaySummaryService.DaySummaryResult result = serviceHandler.doService(certificate, new DaySummaryService(), arg);
		assertEquals(ServiceResult.success().getState(), result.getState());

		DaySummary summary = result.daySummary;
		assertEquals(DayState.WORKING, summary.state());
		assertEquals(480, summary.targetMinutes());
		// started 10 minutes ago, so should be around 10 minutes
		assertEquals(10, summary.actualMinutes());
		assertEquals(1, summary.workEntries().size());
		assertEquals("...", summary.workEntries().getFirst().end());
	}

	@Test
	public void shouldCalculateDaySummaryWithFallbackTargetMinutes() {
		String employeeId = "emp5";
		LocalDate date = LocalDate.of(2026, 2, 2); // Monday

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setId(employeeId);
			employee.setName("Fallback Doe");
			employee.setBoolean(PARAM_ACTIVE, true);
			employee.setDate(PARAM_JOIN_DATE, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			employee.setString(PARAM_TIMEZONE, "Europe/Zurich");
			tx.add(employee);

			Resource schedule = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE_VERSION, true);
			schedule.setName("Schedule");
			schedule.setRelation(PARAM_EMPLOYEE, employee);
			schedule.setDate(PARAM_VALID_FROM, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			// Only set the general dailyTargetMinutes
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES, 480);
			tx.add(schedule);

			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		DaySummaryService.DaySummaryArgument arg = new DaySummaryService.DaySummaryArgument();
		arg.employeeId = employeeId;
		arg.date = date;

		DaySummaryService.DaySummaryResult result = serviceHandler.doService(certificate, new DaySummaryService(), arg);
		assertEquals(ServiceResult.success().getState(), result.getState());

		DaySummary summary = result.daySummary;
		assertEquals(480, summary.targetMinutes());
	}
}
