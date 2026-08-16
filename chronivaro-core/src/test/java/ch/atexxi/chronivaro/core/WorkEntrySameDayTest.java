package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.WorkDayHelper;
import ch.atexxi.chronivaro.core.model.WorkEntryHelper;
import ch.atexxi.chronivaro.core.service.AddWorkEntryService;
import ch.atexxi.chronivaro.core.service.StopTimerService;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static ch.atexxi.chronivaro.core.ChronivaroTestHelper.createEmployee;
import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class WorkEntrySameDayTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + WorkEntrySameDayTest.class.getSimpleName(),
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
	public void shouldFailToManualAddWorkEntrySpanningMultipleDays() {
		String employeeId = "manual-same-day-test";
		ZonedDateTime start = ZonedDateTime.of(LocalDate.now(), LocalTime.of(22, 0), ZoneId.systemDefault());
		ZonedDateTime end = start.plusHours(4); // Spans midnight

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Manual Same Day Test");
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		AddWorkEntryService.AddWorkEntryArgument arg = new AddWorkEntryService.AddWorkEntryArgument();
		arg.employeeId = employeeId;
		arg.start = start;
		arg.end = end;

		ServiceResult result = serviceHandler.doService(certificate, new AddWorkEntryService(), arg);
		assertFalse("Should fail to add entry spanning multiple days", result.isOk());
	}

	@Test
	public void shouldSplitWorkEntryOnMidnightCarryOver() {
		String employeeId = "carry-over-test";
		ZonedDateTime start = ZonedDateTime.of(LocalDate.now().minusDays(1), LocalTime.of(22, 0), ZoneId.systemDefault());
		ZonedDateTime stop = start.plusHours(4); // 02:00 next day

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Carry Over Test");
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Manually create an active entry to simulate timer started yesterday
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			Resource workDay = WorkDayHelper.getOrCreateWorkDay(tx, employee, start);
			Resource workEntry = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
			workEntry.setId(employeeId + "-carry-over");
			workEntry.setRelation(PARAM_EMPLOYEE, employee);
			workEntry.setRelation(PARAM_WORK_DAY, workDay);
			workEntry.setDate(PARAM_START, start);
			workEntry.setString(PARAM_SOURCE, SOURCE_TIMER);
			tx.add(workEntry);
			workDay.addRelation(PARAM_WORK_ENTRIES, workEntry);
			tx.update(workDay);

			employee.setRelation(PARAM_CURRENT_WORK_DAY, workDay);
			tx.update(employee);
			tx.commitOnClose();
		}

		// Stop timer at 02:00 today
		StopTimerService.StopTimerArgument stopArg = new StopTimerService.StopTimerArgument(employeeId);
		stopArg.time = stop;
		ServiceResult stopResult = serviceHandler.doService(certificate, new StopTimerService(), stopArg);
		assertTrue(stopResult.getMessage(), stopResult.isOk());

		// Verify entries
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			List<Resource> workEntries = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(we -> we.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.sorted((we1, we2) -> we1.getDate(PARAM_START).compareTo(we2.getDate(PARAM_START)))
					.toList();

			assertEquals(2, workEntries.size());

			Resource first = workEntries.get(0);
			assertEquals(start, first.getDate(PARAM_START));
			assertEquals(start.toLocalDate().plusDays(1).atStartOfDay(start.getZone()), first.getDate(PARAM_END));

			Resource second = workEntries.get(1);
			assertEquals(start.toLocalDate().plusDays(1).atStartOfDay(start.getZone()), second.getDate(PARAM_START));
			assertEquals(stop, second.getDate(PARAM_END));
		}
	}

	@Test
	public void shouldCapForgottenTimerAtMidnight() {
		String employeeId = "forgotten-timer-test";
		ZonedDateTime start = ZonedDateTime.of(LocalDate.now().minusDays(2), LocalTime.of(22, 0), ZoneId.systemDefault());
		ZonedDateTime stop = start.plusDays(2).plusHours(4); // 2 days later

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Forgotten Timer Test");
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Manually create an active entry to simulate timer started 2 days ago
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			Resource workDay = WorkDayHelper.getOrCreateWorkDay(tx, employee, start);
			Resource workEntry = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
			workEntry.setId(employeeId + "-forgotten");
			workEntry.setRelation(PARAM_EMPLOYEE, employee);
			workEntry.setRelation(PARAM_WORK_DAY, workDay);
			workEntry.setDate(PARAM_START, start);
			workEntry.setString(PARAM_SOURCE, SOURCE_TIMER);
			tx.add(workEntry);
			workDay.addRelation(PARAM_WORK_ENTRIES, workEntry);
			tx.update(workDay);

			employee.setRelation(PARAM_CURRENT_WORK_DAY, workDay);
			tx.update(employee);
			tx.commitOnClose();
		}

		// Stop timer 2 days later
		StopTimerService.StopTimerArgument stopArg = new StopTimerService.StopTimerArgument(employeeId);
		stopArg.time = stop;
		ServiceResult stopResult = serviceHandler.doService(certificate, new StopTimerService(), stopArg);
		assertTrue(stopResult.getMessage(), stopResult.isOk());

		// Verify entry is capped at midnight (because start is 22:00 and target is 0 by default)
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			List<Resource> workEntries = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(we -> we.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.toList();

			assertEquals(1, workEntries.size());
			Resource entry = workEntries.get(0);
			assertEquals(start, entry.getDate(PARAM_START));
			// Since target is 0, start + 0 = start.
			assertEquals(start, entry.getDate(PARAM_END));
			assertEquals("Timer vergessen - auf Sollzeit begrenzt", entry.getString(PARAM_COMMENT));
		}
	}

	@Test
	public void shouldCapForgottenTimerAtDailyTarget() {
		String employeeId = "forgotten-timer-target-test";
		LocalDate startDay = LocalDate.now().minusDays(2);
		ZonedDateTime start = ZonedDateTime.of(startDay, LocalTime.of(8, 0), ZoneId.systemDefault());
		ZonedDateTime stop = start.plusDays(2); // 2 days later

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, employeeId, "Forgotten Timer Target Test");
			Resource schedule = tx.getResourceByRelation(employee, PARAM_CURRENT_SCHEDULE, true);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES, 480); // 8 hours
			tx.update(schedule);
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Manually create an active entry to simulate timer started 2 days ago at 08:00
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			Resource workDay = WorkDayHelper.getOrCreateWorkDay(tx, employee, start);
			Resource workEntry = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
			workEntry.setId(employeeId + "-forgotten-target");
			workEntry.setRelation(PARAM_EMPLOYEE, employee);
			workEntry.setRelation(PARAM_WORK_DAY, workDay);
			workEntry.setDate(PARAM_START, start);
			workEntry.setString(PARAM_SOURCE, SOURCE_TIMER);
			tx.add(workEntry);
			workDay.addRelation(PARAM_WORK_ENTRIES, workEntry);
			tx.update(workDay);

			employee.setRelation(PARAM_CURRENT_WORK_DAY, workDay);
			tx.update(employee);
			tx.commitOnClose();
		}

		// Stop timer 2 days later
		StopTimerService.StopTimerArgument stopArg = new StopTimerService.StopTimerArgument(employeeId);
		stopArg.time = stop;
		ServiceResult stopResult = serviceHandler.doService(certificate, new StopTimerService(), stopArg);
		assertTrue(stopResult.getMessage(), stopResult.isOk());

		// Verify entry is capped at 08:00 + 8 hours = 16:00
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			List<Resource> workEntries = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(we -> we.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.toList();

			assertEquals(1, workEntries.size());
			Resource entry = workEntries.get(0);
			assertEquals(start, entry.getDate(PARAM_START));
			ZonedDateTime expectedEnd = start.plusHours(8);
			assertEquals("End time should be capped at daily target", expectedEnd, entry.getDate(PARAM_END));
			assertEquals("Timer vergessen - auf Sollzeit begrenzt", entry.getString(PARAM_COMMENT));
		}
	}

	@Test
	public void shouldCapForgottenTimerAtMidnightWhenTargetBeyondMidnight() {
		String employeeId = "forgotten-timer-midnight-test";
		LocalDate startDay = LocalDate.now().minusDays(2);
		ZonedDateTime start = ZonedDateTime.of(startDay, LocalTime.of(22, 0), ZoneId.systemDefault());
		ZonedDateTime stop = start.plusDays(2); // 2 days later

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, employeeId, "Forgotten Timer Midnight Test");
			Resource schedule = tx.getResourceByRelation(employee, PARAM_CURRENT_SCHEDULE, true);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES, 480); // 8 hours
			tx.update(schedule);
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Manually create an active entry at 22:00
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			Resource workDay = WorkDayHelper.getOrCreateWorkDay(tx, employee, start);
			Resource workEntry = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
			workEntry.setId(employeeId + "-forgotten-midnight");
			workEntry.setRelation(PARAM_EMPLOYEE, employee);
			workEntry.setRelation(PARAM_WORK_DAY, workDay);
			workEntry.setDate(PARAM_START, start);
			workEntry.setString(PARAM_SOURCE, SOURCE_TIMER);
			tx.add(workEntry);
			workDay.addRelation(PARAM_WORK_ENTRIES, workEntry);
			tx.update(workDay);

			employee.setRelation(PARAM_CURRENT_WORK_DAY, workDay);
			tx.update(employee);
			tx.commitOnClose();
		}

		// Stop timer 2 days later
		StopTimerService.StopTimerArgument stopArg = new StopTimerService.StopTimerArgument(employeeId);
		stopArg.time = stop;
		ServiceResult stopResult = serviceHandler.doService(certificate, new StopTimerService(), stopArg);
		assertTrue(stopResult.getMessage(), stopResult.isOk());

		// Verify entry is capped at midnight, not 06:00 (22:00 + 8h)
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			List<Resource> workEntries = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(we -> we.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.toList();

			assertEquals(1, workEntries.size());
			Resource entry = workEntries.get(0);
			assertEquals(start, entry.getDate(PARAM_START));
			ZonedDateTime expectedEnd = startDay.plusDays(1).atStartOfDay(start.getZone());
			assertEquals("End time should be capped at midnight", expectedEnd, entry.getDate(PARAM_END));
			assertEquals("Timer vergessen - auf Sollzeit begrenzt", entry.getString(PARAM_COMMENT));
		}
	}

	@Test
	public void shouldSetDurationZeroWhenTargetAlreadyReached() {
		String employeeId = "forgotten-timer-zero-test";
		LocalDate startDay = LocalDate.now().minusDays(2);
		ZonedDateTime start1 = ZonedDateTime.of(startDay, LocalTime.of(8, 0), ZoneId.systemDefault());
		ZonedDateTime end1 = start1.plusHours(9); // 9 hours worked, target is 8
		ZonedDateTime start2 = ZonedDateTime.of(startDay, LocalTime.of(18, 0), ZoneId.systemDefault());
		ZonedDateTime stop = start1.plusDays(2); // 2 days later

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, employeeId, "Forgotten Timer Zero Test");
			Resource schedule = tx.getResourceByRelation(employee, PARAM_CURRENT_SCHEDULE, true);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES, 480); // 8 hours
			tx.update(schedule);
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Manually create one finished entry (9h) and one active entry (started at 18:00)
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			Resource workDay = WorkDayHelper.getOrCreateWorkDay(tx, employee, start1);

			Resource we1 = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
			we1.setId(employeeId + "-finished");
			we1.setRelation(PARAM_EMPLOYEE, employee);
			we1.setRelation(PARAM_WORK_DAY, workDay);
			we1.setDate(PARAM_START, start1);
			we1.setDate(PARAM_END, end1);
			tx.add(we1);
			workDay.addRelation(PARAM_WORK_ENTRIES, we1);

			Resource we2 = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
			we2.setId(employeeId + "-forgotten-zero");
			we2.setRelation(PARAM_EMPLOYEE, employee);
			we2.setRelation(PARAM_WORK_DAY, workDay);
			we2.setDate(PARAM_START, start2);
			we2.setString(PARAM_SOURCE, SOURCE_TIMER);
			tx.add(we2);
			workDay.addRelation(PARAM_WORK_ENTRIES, we2);

			tx.update(workDay);
			employee.setRelation(PARAM_CURRENT_WORK_DAY, workDay);
			tx.update(employee);
			tx.commitOnClose();
		}

		// Stop timer 2 days later
		StopTimerService.StopTimerArgument stopArg = new StopTimerService.StopTimerArgument(employeeId);
		stopArg.time = stop;
		ServiceResult stopResult = serviceHandler.doService(certificate, new StopTimerService(), stopArg);
		assertTrue(stopResult.getMessage(), stopResult.isOk());

		// Verify entry is capped at start time (duration 0)
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource entry = tx.getResourceBy(TYPE_WORK_ENTRY, employeeId + "-forgotten-zero", true);
			assertEquals(start2, entry.getDate(PARAM_START));
			assertEquals(start2, entry.getDate(PARAM_END));
			assertEquals("Timer vergessen - auf Sollzeit begrenzt", entry.getString(PARAM_COMMENT));
		}
	}
}
