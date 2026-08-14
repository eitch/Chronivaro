package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.CreateEmployeeService;
import ch.atexxi.chronivaro.core.service.InitiateEmployeeRegistrationService;
import ch.atexxi.chronivaro.core.service.CompleteRegistrationService;
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

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class CompleteRegistrationTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + CompleteRegistrationTest.class.getSimpleName(),
				"src/test/resources");
		runtimeMock.startContainer();
		certificate = runtimeMock.login("admin", "admin");

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource location = tx.getResourceTemplate(TYPE_LOCATION, true);
			location.setId("loc1");
			location.setName("Location 1");
			tx.add(location);

			Resource team = tx.getResourceTemplate(TYPE_TEAM, true);
			team.setId("team1");
			team.setName("Team 1");
			tx.add(team);

			tx.commitOnClose();
		}
	}

	@AfterClass
	public static void afterClass() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	@Test
	public void shouldCompleteRegistration() throws Exception {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		String username = "reguser_complete";

		// Create Employee
		CreateEmployeeService.EmployeeArgument createArg = new CreateEmployeeService.EmployeeArgument();
		createArg.personalNumber = "7890";
		createArg.firstname = "RegComplete";
		createArg.lastname = "User";
		createArg.birthdate = LocalDate.of(1990, 5, 20);
		createArg.teamId = "team1";
		createArg.locationId = "loc1";
		createArg.timezone = "Europe/Zurich";
		createArg.joinDate = LocalDate.of(2026, 1, 1);
		createArg.active = true;
		createArg.username = username;
		createArg.email = "reguser_complete@atexxi.ch";

		ServiceResult createResult = serviceHandler.doService(certificate, new CreateEmployeeService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());

		String employeeId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = tx
					.streamResources(TYPE_EMPLOYEE)
					.filter(r -> r.getName().equals("RegComplete User"))
					.findFirst()
					.orElseThrow();
			employeeId = employee.getId();

			Resource schedule = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			schedule.setId("sched_" + employeeId);
			schedule.setName("Schedule " + employeeId);
			schedule.setRelationId(PARAM_EMPLOYEE, employeeId);
			tx.add(schedule);

			tx.commitOnClose();
		}

		// Initiate Registration
		ServiceResult regResult = serviceHandler.doService(certificate, new InitiateEmployeeRegistrationService(),
				new StringArgument(employeeId));
		assertTrue(regResult.getMessage(), regResult.isOk());

		// Get the challenge code
		TestUserChallengeHandler challengeHandler = TestUserChallengeHandler.getInstance();
		String challengeCode = challengeHandler.getChallenges().values().stream()
				.filter(c -> c.getUser().getUsername().equals(username))
				.map(li.strolch.privilege.model.internal.UserChallenge::getChallenge)
				.findFirst()
				.orElseThrow();

		// Complete Registration
		CompleteRegistrationService.CompleteRegistrationArgument completeArg = new CompleteRegistrationService.CompleteRegistrationArgument();
		completeArg.source = "test";
		completeArg.username = username;
		completeArg.challenge = challengeCode;
		completeArg.password = "newPassword123";

		ServiceResult completeResult = runtimeMock.getAgent().runAsAgentWithResult(ctx -> {
			return serviceHandler.doService(ctx.getCertificate(), new CompleteRegistrationService(), completeArg);
		});
		assertTrue(completeResult.getMessage(), completeResult.isOk());

		// Verify we can login with the new password
		Certificate newCert = runtimeMock.login(username, "newPassword123");
		assertNotNull(newCert);
		runtimeMock.logout(newCert);
	}
}
