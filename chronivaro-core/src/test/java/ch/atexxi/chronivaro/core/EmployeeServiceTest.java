package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.CreateEmployeeService;
import ch.atexxi.chronivaro.core.service.RemoveEmployeeService;
import ch.atexxi.chronivaro.core.service.UpdateEmployeeService;
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

public class EmployeeServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + EmployeeServiceTest.class.getSimpleName(),
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
	public void shouldCreateUpdateAndRemoveEmployee() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// Create
		CreateEmployeeService.EmployeeArgument createArg = new CreateEmployeeService.EmployeeArgument();
		createArg.personalNumber = "123";
		createArg.displayName = "Test Employee";
		createArg.teamId = "team1";
		createArg.locationId = "loc1";
		createArg.timezone = "Europe/Zurich";
		createArg.joinDate = LocalDate.of(2026, 1, 1);
		createArg.active = true;
		createArg.userId = "testuser";

		ServiceResult createResult = serviceHandler.doService(certificate, new CreateEmployeeService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());

		String employeeId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource employee = tx
					.streamResources(TYPE_EMPLOYEE)
					.filter(r -> r.getName().equals("Test Employee"))
					.findFirst()
					.orElseThrow();
			employeeId = employee.getId();
			assertEquals("123", employee.getString(PARAM_PERSONAL_NUMBER));
			assertEquals("Test Employee", employee.getString(PARAM_DISPLAY_NAME));
			assertTrue(employee.getBoolean(PARAM_ACTIVE));
		}

		// Update
		CreateEmployeeService.UpdateEmployeeArgument updateArg = new CreateEmployeeService.UpdateEmployeeArgument();
		updateArg.id = employeeId;
		updateArg.personalNumber = "123";
		updateArg.displayName = "Updated Employee";
		updateArg.teamId = "team1";
		updateArg.locationId = "loc1";
		updateArg.timezone = "Europe/Zurich";
		updateArg.joinDate = LocalDate.of(2026, 1, 1);
		updateArg.active = true;
		updateArg.userId = "testuser";
		ServiceResult updateResult = serviceHandler.doService(certificate, new UpdateEmployeeService(), updateArg);
		assertTrue(updateResult.getMessage(), updateResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			assertEquals("Updated Employee", employee.getString(PARAM_DISPLAY_NAME));
		}

		// Remove
		ServiceResult removeResult = serviceHandler.doService(certificate, new RemoveEmployeeService(),
				new StringArgument(employeeId));
		assertTrue(removeResult.getMessage(), removeResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			assertNull(tx.getResourceBy(TYPE_EMPLOYEE, employeeId));
		}
	}
}
