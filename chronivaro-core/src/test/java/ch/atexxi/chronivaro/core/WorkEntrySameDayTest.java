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

			employee.setRelationId(PARAM_CURRENT_WORK_DAY, workDay.getId());
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

			employee.setRelationId(PARAM_CURRENT_WORK_DAY, workDay.getId());
			tx.update(employee);
			tx.commitOnClose();
		}

		// Stop timer 2 days later
		StopTimerService.StopTimerArgument stopArg = new StopTimerService.StopTimerArgument(employeeId);
		stopArg.time = stop;
		ServiceResult stopResult = serviceHandler.doService(certificate, new StopTimerService(), stopArg);
		assertTrue(stopResult.getMessage(), stopResult.isOk());

		// Verify entry is capped at midnight
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			List<Resource> workEntries = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(we -> we.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.toList();

			assertEquals(1, workEntries.size());
			Resource entry = workEntries.get(0);
			assertEquals(start, entry.getDate(PARAM_START));
			assertEquals(start.toLocalDate().plusDays(1).atStartOfDay(start.getZone()), entry.getDate(PARAM_END));
		}
	}
}
