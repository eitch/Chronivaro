package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.CreateEmployeeService;
import ch.atexxi.chronivaro.core.service.InitiateEmployeeRegistrationService;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class InitiateEmployeeRegistrationServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + InitiateEmployeeRegistrationServiceTest.class.getSimpleName(),
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
	public void shouldInitiateRegistration() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		String username = "reguser";

		// Create Employee
		CreateEmployeeService.EmployeeArgument createArg = new CreateEmployeeService.EmployeeArgument();
		createArg.personalNumber = "789";
		createArg.firstname = "Reg";
		createArg.lastname = "User";
		createArg.birthdate = LocalDate.of(1990, 5, 20);
		createArg.teamId = "team1";
		createArg.locationId = "loc1";
		createArg.timezone = "Europe/Zurich";
		createArg.joinDate = LocalDate.of(2026, 1, 1);
		createArg.active = true;
		createArg.username = username;
		createArg.email = "reguser@atexxi.ch";

		ServiceResult createResult = serviceHandler.doService(certificate, new CreateEmployeeService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());

		String employeeId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource employee = tx
					.streamResources(TYPE_EMPLOYEE)
					.filter(r -> r.getName().equals("Reg User"))
					.findFirst()
					.orElseThrow();
			employeeId = employee.getId();
		}

		// Initiate Registration
		ServiceResult regResult = serviceHandler.doService(certificate, new InitiateEmployeeRegistrationService(),
				new StringArgument(employeeId));
		assertTrue(regResult.getMessage(), regResult.isOk());
	}
}
