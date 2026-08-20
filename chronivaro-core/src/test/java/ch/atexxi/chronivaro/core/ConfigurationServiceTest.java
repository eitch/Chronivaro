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
		arg.annualVacationDays = 30;
		arg.minutesPerVacationDay = 500;
		arg.vacationAbsenceTypeCode = "VAC";
		arg.companyName = "Acme Corp";
		arg.companyLogo = "https://example.com/logo.png";
		arg.defaultLanguage = "en";

		ServiceResult result = serviceHandler.doService(adminCert, service, arg);
		assertTrue(result.getMessage(), result.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", true);
			assertEquals(2400, config.getInteger(PARAM_WEEKLY_TARGET_MINUTES));
			assertEquals(30, config.getInteger(ch.atexxi.chronivaro.core.model.ChronivaroConstants.PARAM_ANNUAL_VACATION_DAYS));
			assertEquals(500, config.getInteger(ch.atexxi.chronivaro.core.model.ChronivaroConstants.PARAM_MINUTES_PER_VACATION_DAY));
			assertEquals("VAC", config.getString(ch.atexxi.chronivaro.core.model.ChronivaroConstants.PARAM_VACATION_ABSENCE_TYPE_CODE));
			assertEquals("Acme Corp", config.getString(ch.atexxi.chronivaro.core.model.ChronivaroConstants.PARAM_COMPANY_NAME));
			assertEquals("https://example.com/logo.png", config.getString(ch.atexxi.chronivaro.core.model.ChronivaroConstants.PARAM_COMPANY_LOGO));
			assertEquals("en", config.getString(ch.atexxi.chronivaro.core.model.ChronivaroConstants.PARAM_DEFAULT_LANGUAGE));
		}
	}

	@Test
	public void shouldRejectInvalidConfigurationValues() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();

		UpdateConfigurationService.UpdateConfigurationArgument invalidWeekly = new UpdateConfigurationService.UpdateConfigurationArgument();
		invalidWeekly.weeklyTargetMinutes = -5;
		ServiceResult result1 = serviceHandler.doService(adminCert, new UpdateConfigurationService(), invalidWeekly);
		assertTrue("Negative weekly target minutes should fail", result1.isNok());

		UpdateConfigurationService.UpdateConfigurationArgument invalidVacationDays = new UpdateConfigurationService.UpdateConfigurationArgument();
		invalidVacationDays.annualVacationDays = 400;
		ServiceResult result2 = serviceHandler.doService(adminCert, new UpdateConfigurationService(), invalidVacationDays);
		assertTrue("Excessive vacation days should fail", result2.isNok());

		UpdateConfigurationService.UpdateConfigurationArgument invalidDayMinutes = new UpdateConfigurationService.UpdateConfigurationArgument();
		invalidDayMinutes.minutesPerVacationDay = 0;
		ServiceResult result3 = serviceHandler.doService(adminCert, new UpdateConfigurationService(), invalidDayMinutes);
		assertTrue("Zero minutes per vacation day should fail", result3.isNok());

		UpdateConfigurationService.UpdateConfigurationArgument blankCode = new UpdateConfigurationService.UpdateConfigurationArgument();
		blankCode.vacationAbsenceTypeCode = "   ";
		ServiceResult result4 = serviceHandler.doService(adminCert, new UpdateConfigurationService(), blankCode);
		assertTrue("Blank absence type code should fail", result4.isNok());

		UpdateConfigurationService.UpdateConfigurationArgument invalidLang = new UpdateConfigurationService.UpdateConfigurationArgument();
		invalidLang.defaultLanguage = "fr";
		ServiceResult result5 = serviceHandler.doService(adminCert, new UpdateConfigurationService(), invalidLang);
		assertTrue("Unsupported default language should fail", result5.isNok());

		UpdateConfigurationService.UpdateConfigurationArgument invalidLogo = new UpdateConfigurationService.UpdateConfigurationArgument();
		invalidLogo.companyLogo = "invalid-uri-scheme:::";
		ServiceResult result6 = serviceHandler.doService(adminCert, new UpdateConfigurationService(), invalidLogo);
		assertTrue("Invalid company logo format should fail", result6.isNok());
	}
}
