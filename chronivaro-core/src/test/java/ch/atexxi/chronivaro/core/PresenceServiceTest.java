package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.PresenceService;
import ch.atexxi.chronivaro.core.service.StartTimerService;
import li.strolch.model.ParameterBag;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PresenceServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + PresenceServiceTest.class.getSimpleName(),
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
	public void shouldCalculatePresence() {
		String teamId = "team-p";
		String locationId = "loc-p";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			// Emp 1: Working
			Resource e1 = new Resource("p-emp1", "Emp 1", TYPE_EMPLOYEE);
			e1.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			e1.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			e1.setBoolean(PARAM_ACTIVE, true);
			e1.setString(BAG_RELATIONS, TYPE_TEAM, teamId);
			e1.setString(BAG_RELATIONS, TYPE_LOCATION, locationId);
			e1.setDate(PARAM_JOIN_DATE, ZonedDateTime.now());
			tx.add(e1);

			// Emp 2: Not working
			Resource e2 = new Resource("p-emp2", "Emp 2", TYPE_EMPLOYEE);
			e2.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			e2.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			e2.setBoolean(PARAM_ACTIVE, true);
			e2.setString(BAG_RELATIONS, TYPE_TEAM, teamId);
			e2.setString(BAG_RELATIONS, TYPE_LOCATION, locationId);
			e2.setDate(PARAM_JOIN_DATE, ZonedDateTime.now());
			tx.add(e2);

			tx.commitOnClose();
		}

		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Start timer for Emp 1
		serviceHandler.doService(certificate, new StartTimerService(), new StringArgument("p-emp1"));

		// Check presence
		PresenceService.PresenceArgument arg = new PresenceService.PresenceArgument();
		arg.teamId = teamId;
		PresenceService.PresenceResult result = serviceHandler.doService(certificate, new PresenceService(), arg);
		assertTrue(result.isOk());
		assertEquals(2, result.presenceInfos.size());

		PresenceService.PresenceInfo info1 = result.presenceInfos
				.stream()
				.filter(i -> i.employeeId().equals("p-emp1"))
				.findFirst()
				.orElseThrow();
		assertEquals(PresenceService.PresenceStatus.WORKING, info1.status());

		PresenceService.PresenceInfo info2 = result.presenceInfos
				.stream()
				.filter(i -> i.employeeId().equals("p-emp2"))
				.findFirst()
				.orElseThrow();
		assertEquals(PresenceService.PresenceStatus.NOT_WORKING, info2.status());
	}
}
