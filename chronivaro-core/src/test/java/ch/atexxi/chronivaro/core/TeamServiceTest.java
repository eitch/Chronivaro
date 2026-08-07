package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.CreateTeamService;
import ch.atexxi.chronivaro.core.service.RemoveTeamService;
import ch.atexxi.chronivaro.core.service.UpdateTeamService;
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

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class TeamServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + TeamServiceTest.class.getSimpleName(),
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
	public void shouldCreateUpdateAndRemoveTeam() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		String teamId = "test-team";

		// Create
		CreateTeamService.TeamArgument createArg = new CreateTeamService.TeamArgument();
		createArg.id = teamId;
		createArg.name = "Test Team";

		ServiceResult createResult = serviceHandler.doService(certificate, new CreateTeamService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource team = tx.getResourceBy(TYPE_TEAM, teamId, true);
			assertEquals("Test Team", team.getString(PARAM_NAME));
		}

		// Update
		createArg.name = "Updated Team";
		ServiceResult updateResult = serviceHandler.doService(certificate, new UpdateTeamService(), createArg);
		assertTrue(updateResult.getMessage(), updateResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource team = tx.getResourceBy(TYPE_TEAM, teamId, true);
			assertEquals("Updated Team", team.getString(PARAM_NAME));
		}

		// Remove
		ServiceResult removeResult = serviceHandler.doService(certificate, new RemoveTeamService(), new StringArgument(teamId));
		assertTrue(removeResult.getMessage(), removeResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			assertNull(tx.getResourceBy(TYPE_TEAM, teamId));
		}
	}
}
