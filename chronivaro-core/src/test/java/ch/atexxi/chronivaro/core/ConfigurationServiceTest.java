package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.service.UpdateConfigurationService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.PARAM_WEEKLY_TARGET_MINUTES;
import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.TYPE_GLOBAL_CONFIGURATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigurationServiceTest {

	private static RuntimeMock runtimeMock;
	private static Certificate adminCert;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + ConfigurationServiceTest.class.getSimpleName(),
				"src/test/resources");
		runtimeMock.startContainer();
		adminCert = runtimeMock.login("admin", "admin");
	}

	@AfterClass
	public static void afterClass() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	@Test
	public void shouldUpdateConfiguration() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		UpdateConfigurationService service = new UpdateConfigurationService();
		UpdateConfigurationService.UpdateConfigurationArgument arg = new UpdateConfigurationService.UpdateConfigurationArgument();
		arg.weeklyTargetMinutes = 2400;

		ServiceResult result = serviceHandler.doService(adminCert, service, arg);
		assertTrue(result.getMessage(), result.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", true);
			assertEquals(2400, config.getInteger(PARAM_WEEKLY_TARGET_MINUTES));
		}
	}
}
