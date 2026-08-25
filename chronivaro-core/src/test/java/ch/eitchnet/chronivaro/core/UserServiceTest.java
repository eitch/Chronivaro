package ch.eitchnet.chronivaro.core;

import ch.eitchnet.chronivaro.core.service.CreateEmployeeService;
import ch.eitchnet.chronivaro.core.service.CreateUserService;
import ch.eitchnet.chronivaro.core.service.InitiateUserRegistrationService;
import ch.eitchnet.chronivaro.core.service.RemoveUserService;
import ch.eitchnet.chronivaro.core.service.UpdateUserService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.base.PrivilegeConstants;
import li.strolch.privilege.model.Certificate;
import li.strolch.privilege.model.UserRep;
import li.strolch.privilege.model.UserState;
import li.strolch.service.StringArgument;
import li.strolch.service.StringResult;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class UserServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + UserServiceTest.class.getSimpleName(),
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
	public void shouldCreateUpdateAndInitiateRegistrationForPureUser() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		String username = "pureadmin";

		// 1. Create pure system user
		CreateUserService.UserArgument createArg = new CreateUserService.UserArgument();
		createArg.username = username;
		createArg.firstname = "Pure";
		createArg.lastname = "Admin";
		createArg.email = "pureadmin@example.com";
		createArg.roles = Set.of(ROLE_ADMINISTRATOR, ROLE_HR);
		createArg.state = UserState.ENABLED;
		createArg.locale = "de";

		StringResult createResult = serviceHandler.doService(certificate, new CreateUserService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());
		String userId = createResult.getValue();
		assertNotNull(userId);

		// Verify user exists in PrivilegeHandler
		UserRep user = runtimeMock.getPrivilegeHandler().getPrivilegeHandler().getUser(certificate, username);
		assertNotNull(user);
		assertEquals(userId, user.getUserId());
		assertEquals("Pure", user.getFirstname());
		assertEquals("Admin", user.getLastname());
		assertEquals("pureadmin@example.com", user.getProperty(PrivilegeConstants.EMAIL));
		assertTrue(user.hasRole(ROLE_ADMINISTRATOR));
		assertTrue(user.hasRole(ROLE_HR));
		assertTrue(user.hasRole(ROLE_MODEL_ACCESSOR));
		assertEquals(UserState.ENABLED, user.getUserState());

		// Verify no Employee resource was created
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			boolean employeeExists = tx.streamResources(TYPE_EMPLOYEE)
					.anyMatch(e -> username.equals(e.getString(PARAM_USERNAME)) || userId.equals(e.getString(PARAM_USER_ID)));
			assertFalse("Pure user must not have an Employee resource", employeeExists);
		}

		// 2. Update user
		UpdateUserService.UpdateUserArgument updateArg = new UpdateUserService.UpdateUserArgument();
		updateArg.userId = userId;
		updateArg.firstname = "UpdatedPure";
		updateArg.lastname = "SuperAdmin";
		updateArg.email = "updated@example.com";
		updateArg.roles = Set.of(ROLE_ADMINISTRATOR, ROLE_SUPERVISOR);
		updateArg.state = UserState.DISABLED;
		updateArg.locale = "en";

		ServiceResult updateResult = serviceHandler.doService(certificate, new UpdateUserService(), updateArg);
		assertTrue(updateResult.getMessage(), updateResult.isOk());

		UserRep updatedUser = runtimeMock.getPrivilegeHandler().getPrivilegeHandler().getUser(certificate, username);
		assertNotNull(updatedUser);
		assertEquals("UpdatedPure", updatedUser.getFirstname());
		assertEquals("SuperAdmin", updatedUser.getLastname());
		assertEquals("updated@example.com", updatedUser.getProperty(PrivilegeConstants.EMAIL));
		assertTrue(updatedUser.hasRole(ROLE_SUPERVISOR));
		assertFalse(updatedUser.hasRole(ROLE_HR));
		assertTrue(updatedUser.hasRole(ROLE_MODEL_ACCESSOR));
		assertEquals(UserState.DISABLED, updatedUser.getUserState());

		// 3. Initiate Registration Challenge (Usage.SET_PASSWORD)
		ServiceResult regResult = serviceHandler.doService(certificate, new InitiateUserRegistrationService(),
				new StringArgument(userId));
		assertTrue(regResult.getMessage(), regResult.isOk());
	}

	@Test
	public void shouldFailToCreateDuplicateUser() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		CreateUserService.UserArgument createArg = new CreateUserService.UserArgument();
		createArg.username = "admin"; // already exists
		createArg.firstname = "Another";
		createArg.lastname = "Admin";
		createArg.roles = Set.of(ROLE_ADMINISTRATOR);

		StringResult createResult = serviceHandler.doService(certificate, new CreateUserService(), createArg);
		assertFalse(createResult.isOk());
		assertTrue(createResult.getMessage().contains("already exists"));
	}

	@Test
	public void shouldRejectSystemUserOperations() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		// 1. Cannot create user with SYSTEM state
		CreateUserService.UserArgument createArg = new CreateUserService.UserArgument();
		createArg.username = "systemuserfail";
		createArg.firstname = "System";
		createArg.lastname = "User";
		createArg.roles = Set.of(ROLE_ADMINISTRATOR);
		createArg.state = UserState.SYSTEM;

		StringResult createResult = serviceHandler.doService(certificate, new CreateUserService(), createArg);
		assertFalse(createResult.isOk());
		assertTrue(createResult.getMessage().contains("Cannot create user with SYSTEM state"));

		// 2. Cannot update system user (e.g. agent)
		UpdateUserService.UpdateUserArgument updateArg = new UpdateUserService.UpdateUserArgument();
		updateArg.userId = "agent";
		updateArg.firstname = "ModifiedAgent";

		ServiceResult updateResult = serviceHandler.doService(certificate, new UpdateUserService(), updateArg);
		assertFalse(updateResult.isOk());
		assertTrue(updateResult.getMessage().contains("not found"));

		// 3. Cannot initiate registration challenge for system user
		ServiceResult regResult = serviceHandler.doService(certificate, new InitiateUserRegistrationService(),
				new StringArgument("agent"));
		assertFalse(regResult.isOk());
		assertTrue(regResult.getMessage().contains("not found"));

		// 4. Cannot delete system user
		ServiceResult deleteResult = serviceHandler.doService(certificate, new RemoveUserService(),
				new StringArgument("agent"));
		assertFalse(deleteResult.isOk());
		assertTrue(deleteResult.getMessage().contains("not found"));
	}

	@Test
	public void shouldDeletePureUserAndAudit() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		String username = "puredeleteme";

		CreateUserService.UserArgument createArg = new CreateUserService.UserArgument();
		createArg.username = username;
		createArg.firstname = "To";
		createArg.lastname = "Delete";
		createArg.roles = Set.of(ROLE_HR);

		StringResult createResult = serviceHandler.doService(certificate, new CreateUserService(), createArg);
		assertTrue(createResult.getMessage(), createResult.isOk());
		String userId = createResult.getValue();

		ServiceResult deleteResult = serviceHandler.doService(certificate, new RemoveUserService(),
				new StringArgument(userId));
		assertTrue(deleteResult.getMessage(), deleteResult.isOk());

		UserRep deletedUser = runtimeMock.getPrivilegeHandler().getPrivilegeHandler().getUser(certificate, username);
		assertNull(deletedUser);

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			boolean audited = tx.streamResources(TYPE_AUDIT_EVENT)
					.anyMatch(r -> "User".equals(r.getString(PARAM_ELEMENT_TYPE))
							&& userId.equals(r.getString(PARAM_ELEMENT_ID))
							&& AUDIT_ACTION_REMOVE.equals(r.getString(PARAM_ACTION)));
			assertTrue("User deletion must be audited", audited);
		}
	}

	@Test
	public void shouldDeleteEmployeeLinkedUserAndDeactivateEmployeeWithoutDataLoss() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		String username = "emplinkeduser";

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource loc = tx.getResourceTemplate(TYPE_LOCATION, true);
			loc.setId("userTestLoc");
			loc.setName("User Test Loc");
			tx.add(loc);

			Resource team = tx.getResourceTemplate(TYPE_TEAM, true);
			team.setId("userTestTeam");
			team.setName("User Test Team");
			tx.add(team);

			tx.commitOnClose();
		}

		CreateEmployeeService.EmployeeArgument empArg = new CreateEmployeeService.EmployeeArgument();
		empArg.personalNumber = "999";
		empArg.firstname = "Linked";
		empArg.lastname = "Employee";
		empArg.birthdate = LocalDate.of(1985, 3, 15);
		empArg.teamId = "userTestTeam";
		empArg.locationId = "userTestLoc";
		empArg.timezone = "Europe/Zurich";
		empArg.joinDate = LocalDate.of(2026, 1, 1);
		empArg.active = true;
		empArg.username = username;

		ServiceResult empResult = serviceHandler.doService(certificate, new CreateEmployeeService(), empArg);
		assertTrue(empResult.getMessage(), empResult.isOk());

		String employeeId;
		String userId;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource employee = tx.streamResources(TYPE_EMPLOYEE)
					.filter(e -> username.equals(e.getString(PARAM_USERNAME)))
					.findFirst()
					.orElseThrow();
			employeeId = employee.getId();
			userId = employee.getString(PARAM_USER_ID);
			assertTrue(employee.getBoolean(PARAM_ACTIVE));
		}

		// Delete the user
		ServiceResult deleteResult = serviceHandler.doService(certificate, new RemoveUserService(),
				new StringArgument(userId));
		assertTrue(deleteResult.getMessage(), deleteResult.isOk());

		// Verify user account is deleted in PrivilegeHandler
		UserRep deletedUser = runtimeMock.getPrivilegeHandler().getPrivilegeHandler().getUser(certificate, username);
		assertNull(deletedUser);

		// Verify Employee resource is preserved, set to active = false, and audited
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId);
			assertNotNull("Employee resource must not be physically removed", employee);
			assertFalse("Employee must be marked inactive", employee.getBoolean(PARAM_ACTIVE));

			boolean empDeactivatedAudited = tx.streamResources(TYPE_AUDIT_EVENT)
					.anyMatch(r -> TYPE_EMPLOYEE.equals(r.getString(PARAM_ELEMENT_TYPE))
							&& employeeId.equals(r.getString(PARAM_ELEMENT_ID))
							&& AUDIT_ACTION_DEACTIVATE.equals(r.getString(PARAM_ACTION)));
			assertTrue("Employee deactivation must be audited", empDeactivatedAudited);

			boolean userDeletionAudited = tx.streamResources(TYPE_AUDIT_EVENT)
					.anyMatch(r -> "User".equals(r.getString(PARAM_ELEMENT_TYPE))
							&& userId.equals(r.getString(PARAM_ELEMENT_ID))
							&& AUDIT_ACTION_REMOVE.equals(r.getString(PARAM_ACTION)));
			assertTrue("User deletion must be audited", userDeletionAudited);
		}
	}
}
