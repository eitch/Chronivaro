package ch.eitchnet.chronivaro.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WebConfirmationDialogsUiTest {

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
	public void shouldVerifyTeamsViewConfirmationDialogUsesName() throws IOException {
		File viewFile = new File(getWebappDir(), "js/pages/TeamsView.js");
		assertTrue("TeamsView.js must exist", viewFile.exists());
		String content = Files.readString(viewFile.toPath());

		assertTrue("TeamsView must look up team name for deletion",
				content.contains("const team = teamsList.find(t => t.id === id);"));
		assertTrue("TeamsView must pass name to teams.confirmDelete",
				content.contains("I18n.t('teams.confirmDelete', { name, id })"));
		assertFalse("TeamsView must not pass bare { id } to confirmDelete",
				content.contains("I18n.t('teams.confirmDelete', { id })"));
	}

	@Test
	public void shouldVerifyLocationsViewConfirmationDialogUsesName() throws IOException {
		File viewFile = new File(getWebappDir(), "js/pages/LocationsView.js");
		assertTrue("LocationsView.js must exist", viewFile.exists());
		String content = Files.readString(viewFile.toPath());

		assertTrue("LocationsView must look up location name for deletion",
				content.contains("const loc = locationsList.find(l => l.id === id);"));
		assertTrue("LocationsView must pass name to locations.confirmDelete",
				content.contains("I18n.t('locations.confirmDelete', { name, id })"));
		assertFalse("LocationsView must not pass bare { id } to confirmDelete",
				content.contains("I18n.t('locations.confirmDelete', { id })"));
	}

	@Test
	public void shouldVerifyAbsenceTypesViewConfirmationDialogUsesName() throws IOException {
		File viewFile = new File(getWebappDir(), "js/pages/AbsenceTypesView.js");
		assertTrue("AbsenceTypesView.js must exist", viewFile.exists());
		String content = Files.readString(viewFile.toPath());

		assertTrue("AbsenceTypesView must look up type name for deletion",
				content.contains("const type = typesList.find(t => t.id === id);"));
		assertTrue("AbsenceTypesView must pass name to absenceTypes.confirmDelete",
				content.contains("I18n.t('absenceTypes.confirmDelete', { name, id })"));
		assertFalse("AbsenceTypesView must not pass bare { id } to confirmDelete",
				content.contains("I18n.t('absenceTypes.confirmDelete', { id })"));
	}

	@Test
	public void shouldVerifyEmployeesViewConfirmationDialogsUseName() throws IOException {
		File viewFile = new File(getWebappDir(), "js/pages/EmployeesView.js");
		assertTrue("EmployeesView.js must exist", viewFile.exists());
		String content = Files.readString(viewFile.toPath());

		assertTrue("EmployeesView must define getEmployeeName helper",
				content.contains("const getEmployeeName = (id) =>"));
		assertTrue("EmployeesView must pass name to employees.confirmDelete",
				content.contains("I18n.t('employees.confirmDelete', { name, id })"));
		assertTrue("EmployeesView must pass name to employees.confirmRegister",
				content.contains("I18n.t('employees.confirmRegister', { name, id })"));
		assertTrue("EmployeesView must pass name to employees.confirmReactivate",
				content.contains("I18n.t('employees.confirmReactivate', { name, id })"));
		assertFalse("EmployeesView must not pass bare { id } to confirmDelete",
				content.contains("I18n.t('employees.confirmDelete', { id })"));
	}

	@Test
	public void shouldVerifyScheduleTemplatesViewConfirmationDialogUsesName() throws IOException {
		File viewFile = new File(getWebappDir(), "js/pages/ScheduleTemplatesView.js");
		assertTrue("ScheduleTemplatesView.js must exist", viewFile.exists());
		String content = Files.readString(viewFile.toPath());

		assertTrue("ScheduleTemplatesView must look up template name for deletion",
				content.contains("const template = templatesList.find(t => t.id === id);"));
		assertTrue("ScheduleTemplatesView must pass name to scheduleTemplates.confirmDelete",
				content.contains("I18n.t('scheduleTemplates.confirmDelete', { name, id })"));
	}

	@Test
	public void shouldVerifyHolidayCalendarsViewConfirmationDialogsUseName() throws IOException {
		File viewFile = new File(getWebappDir(), "js/pages/HolidayCalendarsView.js");
		assertTrue("HolidayCalendarsView.js must exist", viewFile.exists());
		String content = Files.readString(viewFile.toPath());

		assertTrue("HolidayCalendarsView must pass name to confirmDeleteCalendar",
				content.contains("I18n.t('holidayCalendars.confirmDeleteCalendar', { name: cal.name })"));
		assertTrue("HolidayCalendarsView must pass name to confirmDeleteHoliday",
				content.contains("I18n.t('holidayCalendars.confirmDeleteHoliday', { name: hol.name })"));
	}

	@Test
	public void shouldVerifyUsersViewConfirmationDialogsUseUsername() throws IOException {
		File viewFile = new File(getWebappDir(), "js/pages/UsersView.js");
		assertTrue("UsersView.js must exist", viewFile.exists());
		String content = Files.readString(viewFile.toPath());

		assertTrue("UsersView must pass username to users.confirmDelete",
				content.contains("I18n.t('users.confirmDelete', { username })"));
		assertTrue("UsersView must pass username to users.confirmDeleteWithEmployee",
				content.contains("I18n.t('users.confirmDeleteWithEmployee', { username })"));
	}

	@Test
	public void shouldVerifyTranslationKeysContainNameOrUsernamePlaceholders() throws IOException {
		File webappDir = getWebappDir();
		File deFile = new File(webappDir, "i18n/de.json");
		File enFile = new File(webappDir, "i18n/en.json");

		JsonObject deRoot = JsonParser.parseString(Files.readString(deFile.toPath())).getAsJsonObject();
		JsonObject enRoot = JsonParser.parseString(Files.readString(enFile.toPath())).getAsJsonObject();

		// Check teams.confirmDelete
		assertTrue(deRoot.getAsJsonObject("teams").get("confirmDelete").getAsString().contains("{name}"));
		assertTrue(enRoot.getAsJsonObject("teams").get("confirmDelete").getAsString().contains("{name}"));

		// Check locations.confirmDelete
		assertTrue(deRoot.getAsJsonObject("locations").get("confirmDelete").getAsString().contains("{name}"));
		assertTrue(enRoot.getAsJsonObject("locations").get("confirmDelete").getAsString().contains("{name}"));

		// Check absenceTypes.confirmDelete
		assertTrue(deRoot.getAsJsonObject("absenceTypes").get("confirmDelete").getAsString().contains("{name}"));
		assertTrue(enRoot.getAsJsonObject("absenceTypes").get("confirmDelete").getAsString().contains("{name}"));

		// Check employees.confirmDelete, confirmRegister, confirmReactivate
		assertTrue(deRoot.getAsJsonObject("employees").get("confirmDelete").getAsString().contains("{name}"));
		assertTrue(enRoot.getAsJsonObject("employees").get("confirmDelete").getAsString().contains("{name}"));
		assertTrue(deRoot.getAsJsonObject("employees").get("confirmRegister").getAsString().contains("{name}"));
		assertTrue(enRoot.getAsJsonObject("employees").get("confirmRegister").getAsString().contains("{name}"));
		assertTrue(deRoot.getAsJsonObject("employees").get("confirmReactivate").getAsString().contains("{name}"));
		assertTrue(enRoot.getAsJsonObject("employees").get("confirmReactivate").getAsString().contains("{name}"));

		// Check scheduleTemplates.confirmDelete
		assertTrue(deRoot.getAsJsonObject("scheduleTemplates").get("confirmDelete").getAsString().contains("{name}"));
		assertTrue(enRoot.getAsJsonObject("scheduleTemplates").get("confirmDelete").getAsString().contains("{name}"));

		// Check holidayCalendars.confirmDeleteCalendar and confirmDeleteHoliday
		assertTrue(deRoot.getAsJsonObject("holidayCalendars").get("confirmDeleteCalendar").getAsString().contains("{name}"));
		assertTrue(enRoot.getAsJsonObject("holidayCalendars").get("confirmDeleteCalendar").getAsString().contains("{name}"));
		assertTrue(deRoot.getAsJsonObject("holidayCalendars").get("confirmDeleteHoliday").getAsString().contains("{name}"));
		assertTrue(enRoot.getAsJsonObject("holidayCalendars").get("confirmDeleteHoliday").getAsString().contains("{name}"));

		// Check users.confirmDelete
		assertTrue(deRoot.getAsJsonObject("users").get("confirmDelete").getAsString().contains("{username}"));
		assertTrue(enRoot.getAsJsonObject("users").get("confirmDelete").getAsString().contains("{username}"));
	}
}
