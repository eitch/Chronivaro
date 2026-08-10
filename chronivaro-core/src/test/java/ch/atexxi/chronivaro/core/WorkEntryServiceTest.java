package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.WorkEntryHelper;
import ch.atexxi.chronivaro.core.service.StartTimerService;
import ch.atexxi.chronivaro.core.service.StopTimerService;
import li.strolch.model.ParameterBag;
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

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
			Resource employee = new Resource(employeeId, "Jane Doe", TYPE_EMPLOYEE);
			employee.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			employee.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			employee.setBoolean(PARAM_ACTIVE, true);
			employee.setDate(PARAM_JOIN_DATE, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			tx.add(employee);
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
		StringArgument stopArg = new StringArgument(employeeId);
		ServiceResult stopResult = serviceHandler.doService(certificate, new StopTimerService(), stopArg);
		assertTrue(stopResult.getMessage(), stopResult.isOk());

		// Verify No Active Entry and one completed entry
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			assertTrue(WorkEntryHelper.findActiveWorkEntry(tx, employeeId).isEmpty());
			List<Resource> entries = tx
					.streamResources(TYPE_WORK_ENTRY)
					.filter(e -> e.getString(BAG_RELATIONS, TYPE_EMPLOYEE).equals(employeeId))
					.toList();
			assertEquals(1, entries.size());
			assertTrue(entries.getFirst().hasParameter(PARAM_END));
		}
	}
}
