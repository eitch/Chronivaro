package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.DaySummary;
import ch.atexxi.chronivaro.core.service.DaySummaryService;
import li.strolch.model.ParameterBag;
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
import java.util.UUID;

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
			Resource employee = new Resource(employeeId, "Jack Doe", TYPE_EMPLOYEE);
			employee.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			employee.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			employee.setBoolean(PARAM_ACTIVE, true);
			employee.setDate(PARAM_JOIN_DATE, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			employee.setString(PARAM_TIMEZONE, "Europe/Zurich");
			tx.add(employee);

			Resource schedule = new Resource(UUID.randomUUID().toString(), "Schedule",
					TYPE_EMPLOYMENT_SCHEDULE_VERSION);
			schedule.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			schedule.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			schedule.setString(BAG_RELATIONS, PARAM_EMPLOYEE, employeeId);
			schedule.setDate(PARAM_VALID_FROM, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Monday", 480);
			tx.add(schedule);

			// Work Entry 1: 08:00 - 12:00
			Resource e1 = new Resource(UUID.randomUUID().toString(), "E1", TYPE_WORK_ENTRY);
			e1.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			e1.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			e1.setString(BAG_RELATIONS, PARAM_EMPLOYEE, employeeId);
			e1.setDate(PARAM_START, ZonedDateTime.parse("2026-02-02T08:00:00+01:00[Europe/Zurich]"));
			e1.setDate(PARAM_END, ZonedDateTime.parse("2026-02-02T12:00:00+01:00[Europe/Zurich]"));
			tx.add(e1);

			// Work Entry 2: 13:00 - 17:00
			Resource e2 = new Resource(UUID.randomUUID().toString(), "E2", TYPE_WORK_ENTRY);
			e2.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			e2.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			e2.setString(BAG_RELATIONS, PARAM_EMPLOYEE, employeeId);
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
		LocalDate date = LocalDate.of(2026, 2, 2); // Monday

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = new Resource(employeeId, "Jane Doe", TYPE_EMPLOYEE);
			employee.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			employee.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			employee.setBoolean(PARAM_ACTIVE, true);
			employee.setDate(PARAM_JOIN_DATE, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			employee.setString(PARAM_TIMEZONE, "Europe/Zurich");
			tx.add(employee);

			Resource schedule = new Resource(UUID.randomUUID().toString(), "Schedule",
					TYPE_EMPLOYMENT_SCHEDULE_VERSION);
			schedule.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			schedule.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			schedule.setString(BAG_RELATIONS, PARAM_EMPLOYEE, employeeId);
			schedule.setDate(PARAM_VALID_FROM, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES + "Monday", 480);
			tx.add(schedule);

			// Active Work Entry: 08:00 - ...
			Resource e1 = new Resource(UUID.randomUUID().toString(), "E1", TYPE_WORK_ENTRY);
			e1.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			e1.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			e1.setString(BAG_RELATIONS, PARAM_EMPLOYEE, employeeId);
			e1.setDate(PARAM_START, ZonedDateTime.parse("2026-02-02T08:00:00+01:00[Europe/Zurich]"));
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
		assertEquals(480, summary.targetMinutes());
		// 08:00 to end of day (23:59:59...) -> 16 hours -> 960 minutes
		assertEquals(960, summary.actualMinutes());
		assertEquals(1, summary.workEntries().size());
		assertEquals("...", summary.workEntries().getFirst().end());
	}
}
