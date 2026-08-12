package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.ApproveAbsenceService;
import ch.atexxi.chronivaro.core.service.StartTimerService;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RolePermissionTest {

	private static RuntimeMock runtimeMock;
	private static Certificate employeeCert;
	private static Certificate supervisorCert;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + RolePermissionTest.class.getSimpleName(),
				"src/test/resources");
		runtimeMock.startContainer();
		employeeCert = runtimeMock.login("employee", "admin");
		supervisorCert = runtimeMock.login("supervisor", "admin");
	}

	@AfterClass
	public static void afterClass() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	@Test
	public void employeeShouldBeAbleToStartTimer() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		StartTimerService service = new StartTimerService();
		StringArgument arg = new StringArgument("emp1");
		// This might fail for other reasons (e.g. employee missing), but it should NOT fail with AccessDeniedException
		ServiceResult result = serviceHandler.doService(employeeCert, service, arg);
		// If it's AccessDenied, result.isOk() is false and message contains Access denied
		if (!result.isOk()) {
			assertFalse("Should not be Access Denied: " + result.getMessage(), 
					result.getMessage().contains("may not perform service"));
		}
	}

	@Test
	public void employeeShouldNotBeAbleToApproveAbsence() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		ApproveAbsenceService service = new ApproveAbsenceService();
		StringArgument arg = new StringArgument("abs1");
		ServiceResult result = serviceHandler.doService(employeeCert, service, arg);
		assertFalse("Employee should not be able to approve absence", result.isOk());
		assertTrue("Message should indicate Access Denied: " + result.getMessage(), 
				result.getMessage().contains("may not perform service"));
	}

	@Test
	public void supervisorShouldBeAbleToApproveAbsence() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		ApproveAbsenceService service = new ApproveAbsenceService();
		StringArgument arg = new StringArgument("abs1");
		ServiceResult result = serviceHandler.doService(supervisorCert, service, arg);
		if (!result.isOk()) {
			assertFalse("Should not be Access Denied: " + result.getMessage(), 
					result.getMessage().contains("may not perform service"));
		}
	}

	@Test
	public void supervisorShouldNotBeAbleToStartTimer() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		StartTimerService service = new StartTimerService();
		StringArgument arg = new StringArgument("emp1");
		ServiceResult result = serviceHandler.doService(supervisorCert, service, arg);
		assertFalse("Supervisor should not be able to start timer", result.isOk());
		assertTrue("Message should indicate Access Denied: " + result.getMessage(), 
				result.getMessage().contains("may not perform service"));
	}
}
