package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.ApprovePeriodService;
import ch.atexxi.chronivaro.core.service.LockPeriodService;
import ch.atexxi.chronivaro.core.service.SubmitPeriodService;
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

import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PeriodLifecycleServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + PeriodLifecycleServiceTest.class.getSimpleName(),
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
	public void shouldTransitionPeriodLifecycle() {
		String periodId = "period_202608";
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource period = new Resource(periodId, "Period 2026-08", TYPE_TIME_PERIOD);
			period.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			period.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			period.setString(PARAM_STATE, STATE_OPEN);
			tx.add(period);
			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Submit
		ServiceResult result = serviceHandler.doService(certificate, new SubmitPeriodService(), new StringArgument(periodId));
		assertTrue(result.getMessage(), result.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			assertEquals(STATE_SUBMITTED, tx.getResourceBy(TYPE_TIME_PERIOD, periodId, true).getString(PARAM_STATE));
		}

		// Approve
		result = serviceHandler.doService(certificate, new ApprovePeriodService(), new StringArgument(periodId));
		assertTrue(result.getMessage(), result.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			assertEquals(STATE_APPROVED, tx.getResourceBy(TYPE_TIME_PERIOD, periodId, true).getString(PARAM_STATE));
		}

		// Lock
		result = serviceHandler.doService(certificate, new LockPeriodService(), new StringArgument(periodId));
		assertTrue(result.getMessage(), result.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			assertEquals(STATE_LOCKED, tx.getResourceBy(TYPE_TIME_PERIOD, periodId, true).getString(PARAM_STATE));
			
			// Check Audit
			List<Resource> audits = tx.streamResources(TYPE_AUDIT_EVENT)
					.filter(e -> e.getString(PARAM_ELEMENT_TYPE).equals(TYPE_TIME_PERIOD) && e.getString(PARAM_ELEMENT_ID).equals(periodId))
					.toList();
			assertEquals(3, audits.size());
		}
	}
}
