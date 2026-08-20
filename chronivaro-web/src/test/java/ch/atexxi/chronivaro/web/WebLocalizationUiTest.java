package ch.atexxi.chronivaro.web;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WebLocalizationUiTest {

	private File getWebappDir() {
		File dir = new File("src/main/webapp");
		if (dir.exists()) return dir;
		dir = new File("chronivaro-web/src/main/webapp");
		if (dir.exists()) return dir;
		dir = new File("../chronivaro-web/src/main/webapp");
		if (dir.exists()) return dir;
		throw new IllegalStateException("Could not locate chronivaro-web/src/main/webapp directory");
	}

	@Test
	public void shouldVerifyAppShellAndHeaderLanguageSwitcher() throws IOException {
		File indexFile = new File(getWebappDir(), "index.html");
		assertTrue("index.html must exist", indexFile.exists());
		String html = Files.readString(indexFile.toPath());

		// Verify header language switcher select exists
		assertTrue("Header must contain language selector", html.contains("id=\"header-language-select\""));
		assertTrue("Header language selector must contain DE option", html.contains("<option value=\"de\">DE</option>"));
		assertTrue("Header language selector must contain EN option", html.contains("<option value=\"en\">EN</option>"));

		// Verify data-i18n attributes on navigation links
		String[] expectedNavKeys = {
				"nav.dashboard",
				"nav.presence",
				"nav.myTimes",
				"nav.myAbsences",
				"nav.myPeriods",
				"nav.approvals",
				"nav.reports",
				"nav.employees",
				"nav.teams",
				"nav.locations",
				"nav.absenceTypes",
				"nav.holidayCalendars",
				"nav.scheduleTemplates",
				"nav.configuration",
				"nav.logout"
		};

		for (String navKey : expectedNavKeys) {
			assertTrue("Navigation must contain data-i18n attribute for key '" + navKey + "'",
					html.contains("data-i18n=\"" + navKey + "\""));
		}
	}

	@Test
	public void shouldVerifyAppJsLocalizationLifecycle() throws IOException {
		File appJsFile = new File(getWebappDir(), "js/app.js");
		assertTrue("app.js must exist", appJsFile.exists());
		String appJs = Files.readString(appJsFile.toPath());

		assertTrue("app.js must import I18n", appJs.contains("import I18n from './i18n/I18n.js'"));
		assertTrue("app.js must initialize I18n in startup lifecycle", appJs.contains("initI18n"));
		assertTrue("app.js must listen for I18n.onLanguageChange", appJs.contains("I18n.onLanguageChange"));
		assertTrue("app.js must update navigation text using data-i18n", appJs.contains("I18n.t(key)"));
		assertTrue("app.js must sync header language selector value", appJs.contains("header-language-select"));
	}

	@Test
	public void shouldVerifyLoginViewLocalization() throws IOException {
		File loginViewFile = new File(getWebappDir(), "js/pages/LoginView.js");
		assertTrue("LoginView.js must exist", loginViewFile.exists());
		String content = Files.readString(loginViewFile.toPath());

		assertTrue("LoginView must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("LoginView must contain language selector", content.contains("id=\"login-language-select\""));
		assertTrue("LoginView must use auth.loginTitle translation", content.contains("I18n.t('auth.loginTitle')"));
		assertTrue("LoginView must use auth.username translation", content.contains("I18n.t('auth.username')"));
		assertTrue("LoginView must use auth.password translation", content.contains("I18n.t('auth.password')"));
		assertTrue("LoginView must use auth.loginButton translation", content.contains("I18n.t('auth.loginButton')"));
	}

	@Test
	public void shouldVerifyCompleteRegistrationViewLocalization() throws IOException {
		File completeRegFile = new File(getWebappDir(), "js/pages/CompleteRegistrationView.js");
		assertTrue("CompleteRegistrationView.js must exist", completeRegFile.exists());
		String content = Files.readString(completeRegFile.toPath());

		assertTrue("CompleteRegistrationView must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("CompleteRegistrationView must use auth.completeRegistrationTitle",
				content.contains("I18n.t('auth.completeRegistrationTitle')"));
		assertTrue("CompleteRegistrationView must use auth.challengeCode",
				content.contains("I18n.t('auth.challengeCode')"));
		assertTrue("CompleteRegistrationView must use auth.newPassword",
				content.contains("I18n.t('auth.newPassword')"));
		assertTrue("CompleteRegistrationView must use auth.confirmPassword",
				content.contains("I18n.t('auth.confirmPassword')"));
	}

	@Test
	public void shouldVerifyDashboardViewLocalization() throws IOException {
		File dashboardFile = new File(getWebappDir(), "js/pages/DashboardView.js");
		assertTrue("DashboardView.js must exist", dashboardFile.exists());
		String content = Files.readString(dashboardFile.toPath());

		assertTrue("DashboardView must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("DashboardView must localize title", content.contains("I18n.t('dashboard.title')"));
		assertTrue("DashboardView must localize working locations", content.contains("I18n.t('dashboard.workingLocation')"));
		assertTrue("DashboardView must localize start button", content.contains("I18n.t('dashboard.start')"));
		assertTrue("DashboardView must localize stop button", content.contains("I18n.t('dashboard.stop')"));
		assertTrue("DashboardView must localize summary title", content.contains("I18n.t('dashboard.todaySummary')"));
		assertTrue("DashboardView must localize presence states", content.contains("I18n.t('presence.working')"));
		assertTrue("DashboardView must localize off duty badge", content.contains("I18n.t('dashboard.offDuty')"));
	}
}
