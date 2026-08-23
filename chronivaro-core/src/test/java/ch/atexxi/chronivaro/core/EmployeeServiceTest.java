package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.CreateEmployeeService;
import ch.atexxi.chronivaro.core.service.InitiateEmployeeRegistrationService;
import ch.atexxi.chronivaro.core.service.ReactivateEmployeeService;
import ch.atexxi.chronivaro.core.service.RemoveEmployeeService;
import ch.atexxi.chronivaro.core.service.RemoveUserService;
import ch.atexxi.chronivaro.core.service.UpdateEmployeeService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.privilege.model.UserRep;
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
	public void shouldCreateUpdateAndRemoveEmployee() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		String username = "testuser";

		// Create
		CreateEmployeeService.EmployeeArgument createArg = new CreateEmployeeService.EmployeeArgument();
		createArg.personalNumber = "123";
		createArg.firstname = "Test";
		createArg.lastname = "Employee";
		createArg.birthdate = LocalDate.of(1990, 5, 20);
		createArg.teamId = "team1";
		createArg.locationId = "loc1";
		createArg.timezone = "Europe/Zurich";
		createArg.joinDate = LocalDate.of(2026, 1, 1);
		createArg.active = true;
		createArg.username = username;

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
			assertEquals("Test", employee.getString(PARAM_FIRSTNAME));
			assertEquals("Employee", employee.getString(PARAM_LASTNAME));
			assertEquals(LocalDate.of(1990, 5, 20), employee.getDate(PARAM_BIRTHDATE).toLocalDate());
			assertTrue(employee.getBoolean(PARAM_ACTIVE));
			assertNotNull(employee.getString(PARAM_USER_ID));
			assertNotNull(employee.getString(PARAM_USERNAME));

			UserRep user = runtimeMock.getPrivilegeHandler().getPrivilegeHandler().getUser(certificate, username);
			assertNotNull(user);
			assertEquals(user.getUserId(), employee.getString(PARAM_USER_ID));
			assertEquals(user.getUsername(), employee.getString(PARAM_USERNAME));
			assertEquals("Test", user.getFirstname());
			assertEquals("Employee", user.getLastname());
		}

		// Update
		CreateEmployeeService.UpdateEmployeeArgument updateArg = new CreateEmployeeService.UpdateEmployeeArgument();
		updateArg.id = employeeId;
		updateArg.personalNumber = "123";
		updateArg.firstname = "Updated";
		updateArg.lastname = "Employee";
		updateArg.birthdate = null;
		updateArg.teamId = "team1";
		updateArg.locationId = "loc1";
		updateArg.timezone = "Europe/Zurich";
		updateArg.joinDate = LocalDate.of(2026, 1, 1);
		updateArg.active = true;
		updateArg.username = username;
		ServiceResult updateResult = serviceHandler.doService(certificate, new UpdateEmployeeService(), updateArg);
		assertTrue(updateResult.getMessage(), updateResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			assertEquals("Updated", employee.getString(PARAM_FIRSTNAME));
			assertEquals("Employee", employee.getString(PARAM_LASTNAME));
			assertFalse(employee.hasParameter(PARAM_BIRTHDATE));
		}

		// Remove
		ServiceResult removeResult = serviceHandler.doService(certificate, new RemoveEmployeeService(),
				new StringArgument(employeeId));
		assertTrue(removeResult.getMessage(), removeResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			assertNull(tx.getResourceBy(TYPE_EMPLOYEE, employeeId));
		}
	}

	@Test
	public void shouldCreateEmployeeWithoutBirthdate() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		CreateEmployeeService.EmployeeArgument createArg = new CreateEmployeeService.EmployeeArgument();
		createArg.personalNumber = "456";
		createArg.firstname = "No";
		createArg.lastname = "Birthdate";
		createArg.birthdate = null;
		createArg.teamId = "team1";
		createArg.locationId = "loc1";
		createArg.timezone = "Europe/Zurich";
		createArg.joinDate = LocalDate.of(2026, 1, 1);
		createArg.active = true;
		createArg.username = "nobirthdate";

		ServiceResult createResult = serviceHandler.doService(certificate, new CreateEmployeeService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource employee = tx
					.streamResources(TYPE_EMPLOYEE)
					.filter(r -> r.getName().equals("No Birthdate"))
					.findFirst()
					.orElseThrow();
			assertFalse(employee.hasParameter(PARAM_BIRTHDATE));
		}
	}

	@Test
	public void shouldPreventRemovingEmployeeWithHistoricalBookings() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		String username = "empwithhistory";
		CreateEmployeeService.EmployeeArgument createArg = new CreateEmployeeService.EmployeeArgument();
		createArg.personalNumber = "789";
		createArg.firstname = "Has";
		createArg.lastname = "History";
		createArg.teamId = "team1";
		createArg.locationId = "loc1";
		createArg.timezone = "Europe/Zurich";
		createArg.joinDate = LocalDate.of(2026, 1, 1);
		createArg.active = true;
		createArg.username = username;

		ServiceResult createResult = serviceHandler.doService(certificate, new CreateEmployeeService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());

		String employeeId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = tx.streamResources(TYPE_EMPLOYEE)
					.filter(e -> username.equals(e.getString(PARAM_USERNAME)))
					.findFirst()
					.orElseThrow();
			employeeId = employee.getId();

			// Add a historical work day / entry
			Resource workDay = tx.getResourceTemplate(TYPE_WORK_DAY, true);
			workDay.setId("wd-test-hist");
			workDay.setName("2026-01-05");
			workDay.setRelation(PARAM_EMPLOYEE, employee);
			tx.add(workDay);

			tx.commitOnClose();
		}

		// Attempting physical deletion must be blocked
		ServiceResult removeResult = serviceHandler.doService(certificate, new RemoveEmployeeService(),
				new StringArgument(employeeId));
		assertFalse(removeResult.isOk());
		assertTrue(removeResult.getMessage().contains("historical bookings exist"));
	}

	@Test
	public void shouldReactivateInactiveEmployee() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		String username = "reactivateuser";
		CreateEmployeeService.EmployeeArgument createArg = new CreateEmployeeService.EmployeeArgument();
		createArg.personalNumber = "999";
		createArg.firstname = "To";
		createArg.lastname = "Reactivate";
		createArg.email = "reactivate@example.com";
		createArg.teamId = "team1";
		createArg.locationId = "loc1";
		createArg.timezone = "Europe/Zurich";
		createArg.joinDate = LocalDate.of(2026, 1, 1);
		createArg.active = true;
		createArg.username = username;

		ServiceResult createResult = serviceHandler.doService(certificate, new CreateEmployeeService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());

		String employeeId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = tx.streamResources(TYPE_EMPLOYEE)
					.filter(e -> username.equals(e.getString(PARAM_USERNAME)))
					.findFirst()
					.orElseThrow();
			employeeId = employee.getId();

			// Add a schedule
			Resource schedule = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			schedule.setName("Schedule for " + employee.getName());
			schedule.setRelation(PARAM_EMPLOYEE, employee);
			schedule.setDate(PARAM_VALID_FROM, LocalDate.of(2026, 1, 1).atStartOfDay(java.time.ZoneId.of("Europe/Zurich")));
			schedule.setInteger(PARAM_WEEKLY_TARGET_MINUTES, 2400);
			tx.add(schedule);

			tx.commitOnClose();
		}

		// Deactivate employee by removing the linked user account (non-destructive user deletion)
		ServiceResult removeUserResult = serviceHandler.doService(certificate, new RemoveUserService(),
				new StringArgument(username));
		assertTrue(removeUserResult.getMessage(), removeUserResult.isOk());

		// Verify employee is inactive and user does not exist
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			assertFalse(employee.getBoolean(PARAM_ACTIVE));
			UserRep user = runtimeMock.getPrivilegeHandler().getPrivilegeHandler().getUser(certificate, username);
			assertNull(user);
		}

		// Reactivate the employee
		ServiceResult reactivateResult = serviceHandler.doService(certificate, new ReactivateEmployeeService(),
				new StringArgument(employeeId));
		assertTrue(reactivateResult.getMessage(), reactivateResult.isOk());

		// Verify employee is active and user account is recreated
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			assertTrue(employee.getBoolean(PARAM_ACTIVE));
			UserRep user = runtimeMock.getPrivilegeHandler().getPrivilegeHandler().getUser(certificate, username);
			assertNotNull(user);
			assertEquals(username, user.getUsername());
			assertEquals("To", user.getFirstname());
			assertEquals("Reactivate", user.getLastname());
			assertEquals("reactivate@example.com", user.getEmail());
			assertTrue(user.getRoles().contains(ROLE_EMPLOYEE));
		}

		// Verify registration challenge can be initiated on the reactivated employee
		ServiceResult regResult = serviceHandler.doService(certificate, new InitiateEmployeeRegistrationService(),
				new StringArgument(employeeId));
		assertTrue(regResult.getMessage(), regResult.isOk());

		// Attempting to reactivate an already active employee should return an error
		ServiceResult secondReactivateResult = serviceHandler.doService(certificate, new ReactivateEmployeeService(),
				new StringArgument(employeeId));
		assertFalse(secondReactivateResult.isOk());
		assertTrue(secondReactivateResult.getMessage().contains("already active"));
	}
}
