package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.CreateScheduleService;
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

import java.time.ZonedDateTime;
import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CreateScheduleServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + CreateScheduleServiceTest.class.getSimpleName(),
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
	public void shouldCreateSchedule() {
		String employeeId = "emp-create-schedule";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setId(employeeId);
			employee.setName("Schedule Emp");
			employee.setBoolean(PARAM_ACTIVE, true);
			tx.add(employee);
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		CreateScheduleService.CreateScheduleArgument arg = new CreateScheduleService.CreateScheduleArgument();
		arg.employeeId = employeeId;
		arg.validFrom = ZonedDateTime.parse("2026-01-01T00:00:00+01:00[Europe/Zurich]");
		arg.monday = 480;
		arg.tuesday = 480;
		arg.wednesday = 480;
		arg.thursday = 480;
		arg.friday = 480;
		arg.saturday = 0;
		arg.sunday = 0;

		ServiceResult result = serviceHandler.doService(certificate, new CreateScheduleService(), arg);
		assertTrue(result.getMessage(), result.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			List<Resource> schedules = tx
					.streamResources(TYPE_EMPLOYMENT_SCHEDULE)
					.filter(s -> s.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.toList();
			assertEquals(1, schedules.size());
			Resource schedule = schedules.getFirst();
			assertEquals(arg.validFrom, schedule.getDate(PARAM_VALID_FROM));
			assertEquals(480, (int) schedule.getInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY));
			assertEquals(0, (int) schedule.getInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY));
		}
	}
}
