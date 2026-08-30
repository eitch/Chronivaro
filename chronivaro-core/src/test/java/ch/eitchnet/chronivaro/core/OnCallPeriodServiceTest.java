package ch.eitchnet.chronivaro.core;

import ch.eitchnet.chronivaro.core.search.OnCallPeriodSearch;
import ch.eitchnet.chronivaro.core.service.CreateOnCallPeriodService;
import ch.eitchnet.chronivaro.core.service.RemoveOnCallPeriodService;
import ch.eitchnet.chronivaro.core.service.UpdateOnCallPeriodService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

import static ch.eitchnet.chronivaro.core.ChronivaroTestHelper.createEmployee;
import static org.junit.Assert.*;

public class OnCallPeriodServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + OnCallPeriodServiceTest.class.getSimpleName(),
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
	public void shouldCreateUpdateAndRemoveOnCallPeriod() {
		String employeeId = "emp-oncall-1";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			createEmployee(tx, employeeId, "OnCall Tester");
			tx.commitOnClose();
		}

		CreateOnCallPeriodService createService = new CreateOnCallPeriodService();
		CreateOnCallPeriodService.CreateOnCallPeriodArgument createArg = new CreateOnCallPeriodService.CreateOnCallPeriodArgument();
		createArg.employeeId = employeeId;
		createArg.startDate = LocalDate.of(2026, 9, 1);
		createArg.startTime = "08:00";
		createArg.endDate = LocalDate.of(2026, 9, 7);
		createArg.endTime = "17:00";
		createArg.comment = "Initial On-Call Duty";

		ServiceResult createResult = runtimeMock.getServiceHandler().doService(certificate, createService, createArg);
		assertTrue("Create service should succeed", createResult.isOk());

		String periodId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			List<Resource> periods = new OnCallPeriodSearch().forEmployee(employeeId).searchPeriods(tx);
			assertFalse("Should find created period", periods.isEmpty());
			Resource period = periods.get(0);
			periodId = period.getId();
			assertEquals("Initial On-Call Duty", period.getString("comment"));
		}

		UpdateOnCallPeriodService updateService = new UpdateOnCallPeriodService();
		UpdateOnCallPeriodService.UpdateOnCallPeriodArgument updateArg = new UpdateOnCallPeriodService.UpdateOnCallPeriodArgument();
		updateArg.id = periodId;
		updateArg.startDate = LocalDate.of(2026, 9, 1);
		updateArg.startTime = "09:00";
		updateArg.endDate = LocalDate.of(2026, 9, 8);
		updateArg.endTime = "18:00";
		updateArg.comment = "Updated On-Call Duty";

		ServiceResult updateResult = runtimeMock.getServiceHandler().doService(certificate, updateService, updateArg);
		assertTrue("Update service should succeed", updateResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			List<Resource> periods = new OnCallPeriodSearch().forEmployee(employeeId).searchPeriods(tx);
			assertEquals(1, periods.size());
			Resource period = periods.get(0);
			assertEquals("Updated On-Call Duty", period.getString("comment"));
		}

		RemoveOnCallPeriodService removeService = new RemoveOnCallPeriodService();
		StringArgument removeArg = new StringArgument();
		removeArg.value = periodId;

		ServiceResult removeResult = runtimeMock.getServiceHandler().doService(certificate, removeService, removeArg);
		assertTrue("Remove service should succeed", removeResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			List<Resource> periods = new OnCallPeriodSearch().forEmployee(employeeId).searchPeriods(tx);
			assertTrue("Should have no periods remaining", periods.isEmpty());
		}
	}
}
