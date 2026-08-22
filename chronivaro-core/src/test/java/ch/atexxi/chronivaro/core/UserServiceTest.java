package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.CreateUserService;
import ch.atexxi.chronivaro.core.service.InitiateUserRegistrationService;
import ch.atexxi.chronivaro.core.service.UpdateUserService;
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

import java.util.Set;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
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
}
