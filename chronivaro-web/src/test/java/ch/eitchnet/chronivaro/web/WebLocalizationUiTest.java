package ch.eitchnet.chronivaro.web;

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

	@Test
	public void shouldVerifyMyTimesViewLocalization() throws IOException {
		File file = new File(getWebappDir(), "js/pages/MyTimesView.js");
		assertTrue("MyTimesView.js must exist", file.exists());
		String content = Files.readString(file.toPath());

		assertTrue("MyTimesView must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("MyTimesView must localize title", content.contains("I18n.t('times.title')"));
		assertTrue("MyTimesView must localize from filter", content.contains("I18n.t('common.from')"));
		assertTrue("MyTimesView must localize to filter", content.contains("I18n.t('common.to')"));
		assertTrue("MyTimesView must localize refresh button", content.contains("I18n.t('common.refresh')"));
		assertTrue("MyTimesView must localize start time header", content.contains("I18n.t('times.startTime')"));
		assertTrue("MyTimesView must localize end time header", content.contains("I18n.t('times.endTime')"));
		assertTrue("MyTimesView must localize duration header", content.contains("I18n.t('common.duration')"));
		assertTrue("MyTimesView must localize location header", content.contains("I18n.t('times.workingLocation')"));
	}

	@Test
	public void shouldVerifyMyAbsencesViewLocalization() throws IOException {
		File file = new File(getWebappDir(), "js/pages/MyAbsencesView.js");
		assertTrue("MyAbsencesView.js must exist", file.exists());
		String content = Files.readString(file.toPath());

		assertTrue("MyAbsencesView must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("MyAbsencesView must localize title", content.contains("I18n.t('absences.title')"));
		assertTrue("MyAbsencesView must localize request absence button", content.contains("I18n.t('absences.requestAbsence')"));
		assertTrue("MyAbsencesView must localize vacation account title", content.contains("I18n.t('absences.vacationAccount')"));
		assertTrue("MyAbsencesView must localize annual entitlement", content.contains("I18n.t('absences.initialEntitlement')"));
		assertTrue("MyAbsencesView must localize carry over", content.contains("I18n.t('absences.carryOver')"));
		assertTrue("MyAbsencesView must localize adjustments", content.contains("I18n.t('absences.adjustments')"));
		assertTrue("MyAbsencesView must localize used / approved", content.contains("I18n.t('absences.usedApproved')"));
		assertTrue("MyAbsencesView must localize remaining balance", content.contains("I18n.t('absences.currentBalance')"));
		assertTrue("MyAbsencesView must localize journal details summary", content.contains("I18n.t('absences.viewVacationJournal'"));
		assertTrue("MyAbsencesView must localize draft actions", content.contains("I18n.t('absences.saveDraft')"));
	}

	@Test
	public void shouldVerifyMyPeriodsViewLocalization() throws IOException {
		File file = new File(getWebappDir(), "js/pages/MyPeriodsView.js");
		assertTrue("MyPeriodsView.js must exist", file.exists());
		String content = Files.readString(file.toPath());

		assertTrue("MyPeriodsView must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("MyPeriodsView must localize title", content.contains("I18n.t('periods.title')"));
		assertTrue("MyPeriodsView must localize period label", content.contains("I18n.t('periods.period')"));
		assertTrue("MyPeriodsView must localize period status banner", content.contains("I18n.t('periods.periodStatusFor'"));
		assertTrue("MyPeriodsView must localize monthly balance summary", content.contains("I18n.t('periods.monthlyBalanceSummary')"));
		assertTrue("MyPeriodsView must localize daily time breakdown", content.contains("I18n.t('periods.dailyTimeBreakdown')"));
		assertTrue("MyPeriodsView must localize submission closing", content.contains("I18n.t('periods.periodSubmissionClosing')"));
		assertTrue("MyPeriodsView must localize submit button", content.contains("I18n.t('periods.submitPeriodForApproval')"));
	}

	@Test
	public void shouldVerifyPresenceViewLocalization() throws IOException {
		File file = new File(getWebappDir(), "js/pages/PresenceView.js");
		assertTrue("PresenceView.js must exist", file.exists());
		String content = Files.readString(file.toPath());

		assertTrue("PresenceView must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("PresenceView must localize title", content.contains("I18n.t('presence.whoIsWorking')"));
		assertTrue("PresenceView must localize team filter", content.contains("I18n.t('common.team')"));
		assertTrue("PresenceView must localize location filter", content.contains("I18n.t('common.location')"));
		assertTrue("PresenceView must localize working presence", content.contains("I18n.t('presence.working')"));
		assertTrue("PresenceView must localize stats", content.contains("I18n.t('presence.todayStats'"));
	}

	@Test
	public void shouldVerifyApprovalsViewLocalization() throws IOException {
		File file = new File(getWebappDir(), "js/pages/ApprovalsView.js");
		assertTrue("ApprovalsView.js must exist", file.exists());
		String content = Files.readString(file.toPath());

		assertTrue("ApprovalsView must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("ApprovalsView must localize title", content.contains("I18n.t('approvals.supervisorApprovalQueues')"));
		assertTrue("ApprovalsView must localize pending absences tab", content.contains("I18n.t('approvals.pendingAbsences')"));
		assertTrue("ApprovalsView must localize pending periods tab", content.contains("I18n.t('approvals.pendingPeriods')"));
		assertTrue("ApprovalsView must localize approve button", content.contains("I18n.t('common.approve')"));
		assertTrue("ApprovalsView must localize reject button", content.contains("I18n.t('common.reject')"));
		assertTrue("ApprovalsView must localize pagination page info", content.contains("I18n.t('approvals.pageInfo'"));
	}

	@Test
	public void shouldVerifyReportsViewLocalization() throws IOException {
		File file = new File(getWebappDir(), "js/pages/ReportsView.js");
		assertTrue("ReportsView.js must exist", file.exists());
		String content = Files.readString(file.toPath());

		assertTrue("ReportsView must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("ReportsView must localize title", content.contains("I18n.t('reports.reportsAndExport')"));
		assertTrue("ReportsView must localize day report tab", content.contains("I18n.t('reports.dayReport')"));
		assertTrue("ReportsView must localize month report tab", content.contains("I18n.t('reports.monthReport')"));
		assertTrue("ReportsView must localize vacation report tab", content.contains("I18n.t('reports.vacationReport')"));
		assertTrue("ReportsView must localize team report tab", content.contains("I18n.t('reports.teamReport')"));
		assertTrue("ReportsView must localize absences report tab", content.contains("I18n.t('reports.absencesReport')"));
		assertTrue("ReportsView must localize generate report button", content.contains("I18n.t('reports.generateReport')"));
		assertTrue("ReportsView must localize export csv button", content.contains("I18n.t('reports.exportCsvBom')"));
	}

	@Test
	public void shouldVerifyFormatJsLocaleAwareness() throws IOException {
		File file = new File(getWebappDir(), "js/utils/Format.js");
		assertTrue("Format.js must exist", file.exists());
		String content = Files.readString(file.toPath());

		assertTrue("Format.js must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("Format.js date must use getLanguage", content.contains("I18n.getLanguage"));
		assertTrue("Format.js dateTime must use getLanguage", content.contains("I18n.getLanguage"));
	}

	@Test
	public void shouldVerifyEmployeesViewLocalization() throws IOException {
		File file = new File(getWebappDir(), "js/pages/EmployeesView.js");
		assertTrue("EmployeesView.js must exist", file.exists());
		String content = Files.readString(file.toPath());

		assertTrue("EmployeesView must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("EmployeesView must localize title", content.contains("I18n.t('employees.title')"));
		assertTrue("EmployeesView must localize add button", content.contains("I18n.t('employees.addEmployee')"));
		assertTrue("EmployeesView must localize headers", content.contains("I18n.t('employees.username')"));
		assertTrue("EmployeesView must localize initial schedule", content.contains("I18n.t('employees.initialSchedule')"));
	}

	@Test
	public void shouldVerifyTeamsViewLocalization() throws IOException {
		File file = new File(getWebappDir(), "js/pages/TeamsView.js");
		assertTrue("TeamsView.js must exist", file.exists());
		String content = Files.readString(file.toPath());

		assertTrue("TeamsView must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("TeamsView must localize title", content.contains("I18n.t('teams.title')"));
		assertTrue("TeamsView must localize add button", content.contains("I18n.t('teams.addTeam')"));
		assertTrue("TeamsView must localize name header", content.contains("I18n.t('common.name')"));
		assertTrue("TeamsView must localize delete confirmation", content.contains("I18n.t('teams.confirmDelete'"));
	}

	@Test
	public void shouldVerifyLocationsViewLocalization() throws IOException {
		File file = new File(getWebappDir(), "js/pages/LocationsView.js");
		assertTrue("LocationsView.js must exist", file.exists());
		String content = Files.readString(file.toPath());

		assertTrue("LocationsView must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("LocationsView must localize title", content.contains("I18n.t('locations.title')"));
		assertTrue("LocationsView must localize add button", content.contains("I18n.t('locations.addLocation')"));
		assertTrue("LocationsView must localize timezone header", content.contains("I18n.t('locations.timeZone')"));
		assertTrue("LocationsView must localize calendar header", content.contains("I18n.t('locations.holidayCalendar')"));
	}

	@Test
	public void shouldVerifyAbsenceTypesViewLocalization() throws IOException {
		File file = new File(getWebappDir(), "js/pages/AbsenceTypesView.js");
		assertTrue("AbsenceTypesView.js must exist", file.exists());
		String content = Files.readString(file.toPath());

		assertTrue("AbsenceTypesView must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("AbsenceTypesView must localize title", content.contains("I18n.t('absenceTypes.title')"));
		assertTrue("AbsenceTypesView must localize add button", content.contains("I18n.t('absenceTypes.addAbsenceType')"));
		assertTrue("AbsenceTypesView must localize code header", content.contains("I18n.t('absenceTypes.code')"));
		assertTrue("AbsenceTypesView must localize paid checkbox", content.contains("I18n.t('absenceTypes.paid')"));
		assertTrue("AbsenceTypesView must localize approval checkbox", content.contains("I18n.t('absenceTypes.approvalRequired')"));
	}

	@Test
	public void shouldVerifyHolidayCalendarsViewLocalization() throws IOException {
		File file = new File(getWebappDir(), "js/pages/HolidayCalendarsView.js");
		assertTrue("HolidayCalendarsView.js must exist", file.exists());
		String content = Files.readString(file.toPath());

		assertTrue("HolidayCalendarsView must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("HolidayCalendarsView must localize title", content.contains("I18n.t('holidayCalendars.title')"));
		assertTrue("HolidayCalendarsView must localize add calendar button", content.contains("I18n.t('holidayCalendars.addCalendar')"));
		assertTrue("HolidayCalendarsView must localize select prompt", content.contains("I18n.t('holidayCalendars.selectCalendarPrompt')"));
		assertTrue("HolidayCalendarsView must localize add holiday button", content.contains("I18n.t('holidayCalendars.addHoliday')"));
		assertTrue("HolidayCalendarsView must localize credit factor", content.contains("I18n.t('holidayCalendars.creditFactor')"));
	}

	@Test
	public void shouldVerifyScheduleTemplatesViewLocalization() throws IOException {
		File file = new File(getWebappDir(), "js/pages/ScheduleTemplatesView.js");
		assertTrue("ScheduleTemplatesView.js must exist", file.exists());
		String content = Files.readString(file.toPath());

		assertTrue("ScheduleTemplatesView must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("ScheduleTemplatesView must localize title", content.contains("I18n.t('scheduleTemplates.title')"));
		assertTrue("ScheduleTemplatesView must localize add button", content.contains("I18n.t('scheduleTemplates.addTemplate')"));
		assertTrue("ScheduleTemplatesView must localize days", content.contains("I18n.t('scheduleTemplates.mon')"));
		assertTrue("ScheduleTemplatesView must localize monday modal label", content.contains("I18n.t('scheduleTemplates.monday')"));
	}

	@Test
	public void shouldVerifySchedulesViewLocalization() throws IOException {
		File file = new File(getWebappDir(), "js/pages/SchedulesView.js");
		assertTrue("SchedulesView.js must exist", file.exists());
		String content = Files.readString(file.toPath());

		assertTrue("SchedulesView must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("SchedulesView must localize back button", content.contains("I18n.t('schedules.backToEmployees')"));
		assertTrue("SchedulesView must localize dynamic title", content.contains("I18n.t('schedules.schedulesFor'"));
		assertTrue("SchedulesView must localize add button", content.contains("I18n.t('schedules.addSchedule')"));
		assertTrue("SchedulesView must localize weekly total footer", content.contains("I18n.t('schedules.weeklyTotal')"));
	}

	@Test
	public void shouldVerifyConfigurationViewLocalization() throws IOException {
		File file = new File(getWebappDir(), "js/pages/ConfigurationView.js");
		assertTrue("ConfigurationView.js must exist", file.exists());
		String content = Files.readString(file.toPath());

		assertTrue("ConfigurationView must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("ConfigurationView must localize title", content.contains("I18n.t('configuration.title')"));
		assertTrue("ConfigurationView must localize subtitle", content.contains("I18n.t('configuration.subtitle')"));
		assertTrue("ConfigurationView must localize global settings", content.contains("I18n.t('configuration.globalSettings')"));
		assertTrue("ConfigurationView must localize company name", content.contains("I18n.t('configuration.companyName')"));
		assertTrue("ConfigurationView must localize default language", content.contains("I18n.t('configuration.defaultLanguage')"));
		assertTrue("ConfigurationView must localize save button", content.contains("I18n.t('configuration.saveConfig')"));
		assertTrue("ConfigurationView must localize reload button", content.contains("I18n.t('configuration.reloadConfig')"));
	}

	@Test
	public void shouldVerifyLanguagePersistenceAndSync() throws IOException {
		File authApiFile = new File(getWebappDir(), "js/api/AuthApi.js");
		assertTrue("AuthApi.js must exist", authApiFile.exists());
		String authApiContent = Files.readString(authApiFile.toPath());
		assertTrue("AuthApi must have updateLanguage", authApiContent.contains("updateLanguage(language)"));
		assertTrue("AuthApi updateLanguage must call rest/chronivaro/v1/auth/language",
				authApiContent.contains("rest/chronivaro/v1/auth/language"));

		File i18nFile = new File(getWebappDir(), "js/i18n/I18n.js");
		assertTrue("I18n.js must exist", i18nFile.exists());
		String i18nContent = Files.readString(i18nFile.toPath());
		assertTrue("I18n.js must import AuthApi", i18nContent.contains("import AuthApi from '../api/AuthApi.js'"));
		assertTrue("I18n.setLanguage must sync language to AuthApi", i18nContent.contains("AuthApi.updateLanguage"));

		File loginViewFile = new File(getWebappDir(), "js/pages/LoginView.js");
		assertTrue("LoginView.js must exist", loginViewFile.exists());
		String loginViewContent = Files.readString(loginViewFile.toPath());
		assertTrue("LoginView must update backend language on login",
				loginViewContent.contains("AuthApi.updateLanguage(activeLang)"));
	}
}
