package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.WorkEntryHelper;
import ch.atexxi.chronivaro.core.model.WorkingLocation;
import ch.atexxi.chronivaro.core.service.StartTimerService;
import ch.atexxi.chronivaro.core.service.StopTimerService;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

import static ch.atexxi.chronivaro.core.ChronivaroTestHelper.createEmployee;
import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TimerWorkDayTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + TimerWorkDayTest.class.getSimpleName(),
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
	public void shouldManageWorkDayWithTimer() {
		String employeeId = "timer-wd-test";
		LocalDate today = LocalDate.now();
		String expectedWorkDayId = employeeId + "-" + today.format(DateTimeFormatter.ISO_LOCAL_DATE);

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Timer WD Test");
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// 1. Start Timer
		ServiceResult startResult1 = serviceHandler.doService(certificate, new StartTimerService(),
				new StartTimerService.Argument(employeeId, WorkingLocation.OFFICE));
		assertTrue(startResult1.getMessage(), startResult1.isOk());

		// Verify WorkDay and relation
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			assertEquals(expectedWorkDayId, employee.getRelationId(PARAM_CURRENT_WORK_DAY));

			Resource workDay = tx.getResourceBy(TYPE_WORK_DAY, expectedWorkDayId, true);
			assertEquals(employeeId, workDay.getRelationId(PARAM_EMPLOYEE));
			assertEquals(today, workDay.getDate(PARAM_DATE).toLocalDate());

			Resource workEntry = WorkEntryHelper.findActiveWorkEntry(tx, employeeId).orElseThrow();
			assertEquals(expectedWorkDayId, workEntry.getRelationId(PARAM_WORK_DAY));
		}

		// 2. Stop Timer
		ServiceResult stopResult1 = serviceHandler.doService(certificate, new StopTimerService(),
				new StopTimerService.StopTimerArgument(employeeId));
		assertTrue(stopResult1.getMessage(), stopResult1.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			assertTrue(WorkEntryHelper.findActiveWorkEntry(tx, employeeId).isEmpty());
		}

		// 3. Start Timer Again (same day)
		ServiceResult startResult2 = serviceHandler.doService(certificate, new StartTimerService(),
				new StartTimerService.Argument(employeeId, WorkingLocation.OFFICE));
		assertTrue(startResult2.getMessage(), startResult2.isOk());

		// Verify Same WorkDay is reused
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			assertEquals(expectedWorkDayId, employee.getRelationId(PARAM_CURRENT_WORK_DAY));

			List<Resource> workEntries = tx
					.streamResources(TYPE_WORK_ENTRY)
					.filter(we -> we.getRelationId(PARAM_WORK_DAY).equals(expectedWorkDayId))
					.toList();
			assertEquals(2, workEntries.size());
		}
	}
}
