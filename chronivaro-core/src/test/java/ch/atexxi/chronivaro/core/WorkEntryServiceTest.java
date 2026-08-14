package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.WorkEntryHelper;
import ch.atexxi.chronivaro.core.service.StartTimerService;
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

import java.time.ZonedDateTime;
import java.util.List;

import static ch.atexxi.chronivaro.core.ChronivaroTestHelper.createEmployee;
import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class WorkEntryServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + WorkEntryServiceTest.class.getSimpleName(),
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
	public void shouldStartAndStopTimer() {
		String employeeId = "emp2";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Jane Doe");
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Start Timer
		StringArgument startArg = new StringArgument(employeeId);
		ServiceResult startResult = serviceHandler.doService(certificate, new StartTimerService(), startArg);
		assertTrue(startResult.getMessage(), startResult.isOk());

		// Verify Active Entry
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			assertTrue(WorkEntryHelper.findActiveWorkEntry(tx, employeeId).isPresent());
		}

		// Stop Timer
		StopTimerService.StopTimerArgument stopArg = new StopTimerService.StopTimerArgument(employeeId);
		ServiceResult stopResult = serviceHandler.doService(certificate, new StopTimerService(), stopArg);
		assertTrue(stopResult.getMessage(), stopResult.isOk());

		// Verify No Active Entry and one completed entry
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			assertTrue(WorkEntryHelper.findActiveWorkEntry(tx, employeeId).isEmpty());
			List<Resource> entries = tx
					.streamResources(TYPE_WORK_ENTRY)
					.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.toList();
			assertEquals(1, entries.size());
			assertTrue(entries.getFirst().hasParameter(PARAM_END));
		}
	}

	@Test
	public void shouldNotStartTimerTwice() {
		String employeeId = "emp1";
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "Emp 1", ZonedDateTime.now());
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		ServiceResult result1 = serviceHandler.doService(certificate, new StartTimerService(),
				new StringArgument(employeeId));
		assertTrue(result1.isOk());

		ServiceResult result2 = serviceHandler.doService(certificate, new StartTimerService(),
				new StringArgument(employeeId));
		assertFalse(result2.isOk());
		assertTrue(result2.getMessage().contains("An active work entry already exists"));
	}
}
