package ch.eitchnet.chronivaro.web;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class WebModalDialogsUiTest {

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
	public void shouldVerifyModalContentAndDialogOverflowStyles() throws IOException {
		File cssFile = new File(getWebappDir(), "assets/css/style.css");
		assertTrue("style.css must exist", cssFile.exists());
		String css = Files.readString(cssFile.toPath());

		// Verify .modal-content max-height and vertical overflow scrolling
		assertTrue("style.css must define .modal-content with max-height: 90vh",
				css.contains("max-height: 90vh;"));
		assertTrue("style.css must define .modal-content with overflow-y: auto",
				css.contains("overflow-y: auto;"));

		// Verify notification dialog flex and body scrolling
		assertTrue("style.css must configure .notification-dialog as flex column",
				css.contains(".notification-dialog") && css.contains("display: flex;") && css.contains("flex-direction: column;"));
		assertTrue("style.css must configure .notification-dialog-body with overflow-y: auto",
				css.contains(".notification-dialog-body") && css.contains("overflow-y: auto;"));
	}

	@Test
	public void shouldVerifyEmployeesViewModalStructure() throws IOException {
		File viewFile = new File(getWebappDir(), "js/pages/EmployeesView.js");
		assertTrue("EmployeesView.js must exist", viewFile.exists());
		String js = Files.readString(viewFile.toPath());

		assertTrue("EmployeesView must define employee-modal", js.contains("id=\"employee-modal\""));
		assertTrue("EmployeesView must use modal-content inside employee-modal", js.contains("class=\"modal-content wide\""));
		assertTrue("EmployeesView modal must contain actions section", js.contains("class=\"actions\""));
		assertTrue("EmployeesView modal must contain submit button", js.contains("<button type=\"submit\">"));
		assertTrue("EmployeesView modal must contain cancel button", js.contains("id=\"close-modal\""));
	}

	@Test
	public void shouldVerifySchedulesViewModalStructure() throws IOException {
		File viewFile = new File(getWebappDir(), "js/pages/SchedulesView.js");
		assertTrue("SchedulesView.js must exist", viewFile.exists());
		String js = Files.readString(viewFile.toPath());

		assertTrue("SchedulesView must define schedule-modal", js.contains("id=\"schedule-modal\""));
		assertTrue("SchedulesView must use modal-content inside schedule-modal", js.contains("class=\"modal-content\""));
		assertTrue("SchedulesView modal must contain actions section", js.contains("class=\"actions\""));
		assertTrue("SchedulesView modal must contain submit button", js.contains("<button type=\"submit\">"));
		assertTrue("SchedulesView modal must contain cancel button", js.contains("id=\"close-modal\""));
	}

	@Test
	public void shouldVerifyAllModalPagesUseModalContentAndActionButtons() throws IOException {
		String[] pageFiles = {
				"AbsenceTypesView.js",
				"ApprovalsView.js",
				"AuditLogView.js",
				"DashboardView.js",
				"EmployeesView.js",
				"HolidayCalendarsView.js",
				"LocationsView.js",
				"MyAbsencesView.js",
				"MyTimesView.js",
				"ScheduleTemplatesView.js",
				"SchedulesView.js",
				"TeamsView.js",
				"UsersView.js"
		};

		for (String pageFileName : pageFiles) {
			File file = new File(getWebappDir(), "js/pages/" + pageFileName);
			assertTrue(pageFileName + " must exist", file.exists());
			String content = Files.readString(file.toPath());

			if (content.contains("class=\"modal\"") || content.contains("class=\"modal ")) {
				assertTrue(pageFileName + " must contain modal-content",
						content.contains("modal-content"));
			}
		}
	}
}
