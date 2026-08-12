package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.CreateScheduleService;
import ch.atexxi.chronivaro.core.service.RemoveScheduleService;
import ch.atexxi.chronivaro.core.service.UpdateScheduleService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static ch.atexxi.chronivaro.core.ChronivaroTestHelper.createEmployee;
import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class UpdateAndRemoveScheduleServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + UpdateAndRemoveScheduleServiceTest.class.getSimpleName(),
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
	public void shouldUpdateAndRemoveSchedule() {
		String employeeId = "emp-update-schedule";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Schedule Emp", false);
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Create
		CreateScheduleService.CreateScheduleArgument createArg = new CreateScheduleService.CreateScheduleArgument();
		createArg.employeeId = employeeId;
		createArg.validFrom = ZonedDateTime.parse("2026-01-01T00:00:00+01:00[Europe/Zurich]");
		createArg.monday = 480;
		createArg.tuesday = 480;
		createArg.wednesday = 480;
		createArg.thursday = 480;
		createArg.friday = 480;
		createArg.saturday = 0;
		createArg.sunday = 0;

		ServiceResult createResult = serviceHandler.doService(certificate, new CreateScheduleService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());

		String scheduleId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			List<Resource> schedules = tx
					.streamResources(TYPE_EMPLOYMENT_SCHEDULE)
					.filter(s -> s.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.toList();
			assertEquals(1, schedules.size());
			scheduleId = schedules.getFirst().getId();
		}

		// Update
		UpdateScheduleService.UpdateScheduleArgument updateArg = new UpdateScheduleService.UpdateScheduleArgument();
		updateArg.id = scheduleId;
		updateArg.validFrom = ZonedDateTime.parse("2026-01-01T00:00:00+01:00[Europe/Zurich]");
		updateArg.validTo = ZonedDateTime.parse("2026-12-31T23:59:59+01:00[Europe/Zurich]");
		updateArg.monday = 400;
		updateArg.tuesday = 400;
		updateArg.wednesday = 400;
		updateArg.thursday = 400;
		updateArg.friday = 400;
		updateArg.saturday = 60;
		updateArg.sunday = 60;

		ServiceResult updateResult = serviceHandler.doService(certificate, new UpdateScheduleService(), updateArg);
		assertTrue(updateResult.getMessage(), updateResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource schedule = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE, scheduleId);
			assertEquals(updateArg.validFrom, schedule.getDate(PARAM_VALID_FROM));
			assertEquals(updateArg.validTo, schedule.getDate(PARAM_VALID_TO));
			assertEquals(400, (int) schedule.getInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY));
			assertEquals(60, (int) schedule.getInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY));
		}

		// Remove
		ServiceResult removeResult = serviceHandler.doService(certificate, new RemoveScheduleService(), new StringArgument(scheduleId));
		assertTrue(removeResult.getMessage(), removeResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource schedule = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE, scheduleId);
			assertNull(schedule);
		}
	}

	@Test
	public void shouldFailToRemoveScheduleWithWorkEntries() {
		String employeeId = "emp-remove-fail";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Remove Fail Emp", false);
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Create Schedule
		CreateScheduleService.CreateScheduleArgument createArg = new CreateScheduleService.CreateScheduleArgument();
		createArg.employeeId = employeeId;
		createArg.validFrom = ZonedDateTime.parse("2026-01-01T00:00:00+01:00[Europe/Zurich]");
		createArg.monday = 480;
		createArg.tuesday = 480;
		createArg.wednesday = 480;
		createArg.thursday = 480;
		createArg.friday = 480;
		createArg.saturday = 0;
		createArg.sunday = 0;
		serviceHandler.doService(certificate, new CreateScheduleService(), createArg);

		String scheduleId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			scheduleId = tx
					.streamResources(TYPE_EMPLOYMENT_SCHEDULE)
					.filter(s -> s.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.findFirst()
					.get()
					.getId();
		}

		// Create Work Entry within schedule period
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource workEntry = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
			workEntry.setId("we-fail-remove");
			workEntry.setRelationId(PARAM_EMPLOYEE, employeeId);
			workEntry.setDate(PARAM_START, ZonedDateTime.parse("2026-01-15T08:00:00+01:00[Europe/Zurich]"));
			workEntry.setDate(PARAM_END, ZonedDateTime.parse("2026-01-15T12:00:00+01:00[Europe/Zurich]"));
			tx.add(workEntry);
			tx.commitOnClose();
		}

		// Remove should fail
		ServiceResult removeResult = serviceHandler.doService(certificate, new RemoveScheduleService(),
				new StringArgument(scheduleId));
		assertFalse(removeResult.getMessage(), removeResult.isOk());
		assertTrue(removeResult.getMessage(), removeResult.getMessage().contains("Cannot delete schedule because it has 1 work entries associated with it"));
	}

	@Test
	public void shouldVersionScheduleOnUpdate() {
		String employeeId = "emp-version-update";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Version Update Emp", false);
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// 1. Create Initial Schedule
		CreateScheduleService.CreateScheduleArgument createArg = new CreateScheduleService.CreateScheduleArgument();
		createArg.employeeId = employeeId;
		createArg.validFrom = ZonedDateTime.parse("2026-01-01T00:00:00+01:00[Europe/Zurich]");
		createArg.monday = 480;
		createArg.tuesday = 480;
		createArg.wednesday = 480;
		createArg.thursday = 480;
		createArg.friday = 480;
		createArg.saturday = 0;
		createArg.sunday = 0;
		serviceHandler.doService(certificate, new CreateScheduleService(), createArg);

		String initialScheduleId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			initialScheduleId = tx
					.streamResources(TYPE_EMPLOYMENT_SCHEDULE)
					.filter(s -> s.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.findFirst()
					.get()
					.getId();
		}

		// 2. Update with later validFrom -> should version
		UpdateScheduleService.UpdateScheduleArgument updateArg = new UpdateScheduleService.UpdateScheduleArgument();
		updateArg.id = initialScheduleId;
		updateArg.validFrom = ZonedDateTime.parse("2026-02-01T00:00:00+01:00[Europe/Zurich]");
		updateArg.monday = 420; // Change target minutes
		updateArg.tuesday = 420;
		updateArg.wednesday = 420;
		updateArg.thursday = 420;
		updateArg.friday = 420;
		updateArg.saturday = 0;
		updateArg.sunday = 0;

		ServiceResult updateResult = serviceHandler.doService(certificate, new UpdateScheduleService(), updateArg);
		assertTrue(updateResult.getMessage(), updateResult.isOk());

		// 3. Verify
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource oldVersion = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE, initialScheduleId, true);
			assertTrue(oldVersion.hasParameter(PARAM_VALID_TO));
			assertEquals("2026-01-31", oldVersion.getDate(PARAM_VALID_TO).toLocalDate().toString());

			List<Resource> versions = tx.streamResources(TYPE_EMPLOYMENT_SCHEDULE)
					.filter(s -> s.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.toList();
			assertEquals(2, versions.size());

			Resource newVersion = versions.stream()
					.filter(v -> !v.getId().equals(initialScheduleId))
					.findFirst()
					.get();
			assertEquals("2026-02-01", newVersion.getDate(PARAM_VALID_FROM).toLocalDate().toString());
			assertEquals(420, newVersion.getInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY));
		}
	}

	@Test
	public void shouldMaintainCurrentSchedule() {
		String employeeId = "emp-current-schedule";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Current Schedule Emp", false);
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// 1. Create schedule valid from 1 year ago -> should set currentSchedule
		CreateScheduleService.CreateScheduleArgument createArg = new CreateScheduleService.CreateScheduleArgument();
		createArg.employeeId = employeeId;
		createArg.validFrom = ZonedDateTime.now().minusYears(1).withHour(0).withMinute(0).withSecond(0);
		createArg.monday = 480;
		createArg.tuesday = 480;
		createArg.wednesday = 480;
		createArg.thursday = 480;
		createArg.friday = 480;
		createArg.saturday = 0;
		createArg.sunday = 0;
		serviceHandler.doService(certificate, new CreateScheduleService(), createArg);

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			assertTrue("Employee should have currentSchedule relation", employee.hasRelation(PARAM_CURRENT_SCHEDULE));
			String scheduleId = employee.getRelationId(PARAM_CURRENT_SCHEDULE);

			Resource schedule = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE, scheduleId, true);
			assertEquals(employeeId, schedule.getRelationId(PARAM_EMPLOYEE));
		}

		// 2. Create new version valid from tomorrow -> currentSchedule should NOT change yet
		String oldScheduleId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			oldScheduleId = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true).getRelationId(PARAM_CURRENT_SCHEDULE);
		}

		UpdateScheduleService.UpdateScheduleArgument updateArg = new UpdateScheduleService.UpdateScheduleArgument();
		updateArg.id = oldScheduleId;
		updateArg.validFrom = ZonedDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0);
		updateArg.monday = 420;
		updateArg.tuesday = 420;
		updateArg.wednesday = 420;
		updateArg.thursday = 420;
		updateArg.friday = 420;
		updateArg.saturday = 0;
		updateArg.sunday = 0;
		serviceHandler.doService(certificate, new UpdateScheduleService(), updateArg);

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			assertEquals("currentSchedule should still be the old one", oldScheduleId, employee.getRelationId(PARAM_CURRENT_SCHEDULE));
		}
	}
}
