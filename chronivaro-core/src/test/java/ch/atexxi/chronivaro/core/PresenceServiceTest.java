package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.PresenceService;
import ch.atexxi.chronivaro.core.service.StartTimerService;
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
 
import static ch.atexxi.chronivaro.core.ChronivaroTestHelper.createEmployee;
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
			// Team
			Resource team = tx.getResourceTemplate(TYPE_TEAM, true);
			team.setId(teamId);
			team.setName("Test Team");
			tx.add(team);

			// Emp 1: Working
			Resource e1 = createEmployee(tx, "p-emp1", "Emp 1", ZonedDateTime.now());
			e1 = tx.readLock(e1);
			e1.setRelationId(PARAM_LOCATION, locationId);
			e1.setRelation(PARAM_PRIMARY_TEAM, team);
			tx.update(e1);
 
			Resource s1 = tx.readLock(tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE, e1.getRelationId(PARAM_CURRENT_SCHEDULE), true));
			s1.setInteger(PARAM_DAILY_TARGET_MINUTES + "Monday", 480);
			tx.update(s1);
 
			// Emp 2: Not working
			Resource e2 = createEmployee(tx, "p-emp2", "Emp 2", ZonedDateTime.now());
			e2 = tx.readLock(e2);
			e2.setRelationId(PARAM_LOCATION, locationId);
			e2.setRelation(PARAM_PRIMARY_TEAM, team);
			tx.update(e2);
 
			Resource s2 = tx.readLock(tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE, e2.getRelationId(PARAM_CURRENT_SCHEDULE), true));
			s2.setInteger(PARAM_DAILY_TARGET_MINUTES + "Monday", 480);
			tx.update(s2);

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
		assertTrue(info1.minutesToday() >= 0);

		PresenceService.PresenceInfo info2 = result.presenceInfos
				.stream()
				.filter(i -> i.employeeId().equals("p-emp2"))
				.findFirst()
				.orElseThrow();
		assertEquals(PresenceService.PresenceStatus.NOT_WORKING, info2.status());
		assertEquals(0, info2.minutesToday());
	}
}
