package ch.eitchnet.chronivaro.web;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class WebTimesUiTest {

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
	public void shouldVerifyWorkEntryModalAndCalendarInputStyling() throws IOException {
		File viewFile = new File(getWebappDir(), "js/pages/MyTimesView.js");
		assertTrue("MyTimesView.js must exist", viewFile.exists());
		String viewJs = Files.readString(viewFile.toPath());

		// Verify work entry edit modal elements
		assertTrue("MyTimesView must have work-entry-modal", viewJs.contains("id=\"work-entry-modal\""));
		assertTrue("MyTimesView must have modal-end-time datetime-local input",
				viewJs.contains("input type=\"datetime-local\" id=\"modal-end-time\""));

		// Verify manager team and employee selection filters
		assertTrue("MyTimesView must have team filter for managers", viewJs.contains("id=\"times-team-filter\""));
		assertTrue("MyTimesView must have employee filter for managers", viewJs.contains("id=\"times-employee-filter\""));

		// Verify add work entry modal elements
		assertTrue("MyTimesView must have add work entry button", viewJs.contains("id=\"btn-add-work-entry\""));
		assertTrue("MyTimesView must have add work entry modal", viewJs.contains("id=\"add-work-entry-modal\""));
		assertTrue("MyTimesView must have add-start-time input", viewJs.contains("id=\"add-start-time\""));
		assertTrue("MyTimesView must have add-end-time input", viewJs.contains("id=\"add-end-time\""));

		// Verify WorkEntryApi extensions
		File apiFile = new File(getWebappDir(), "js/api/WorkEntryApi.js");
		assertTrue("WorkEntryApi.js must exist", apiFile.exists());
		String apiJs = Files.readString(apiFile.toPath());
		assertTrue("WorkEntryApi must have getEmployeeWorkEntries", apiJs.contains("getEmployeeWorkEntries"));
		assertTrue("WorkEntryApi must have createEmployeeWorkEntry", apiJs.contains("createEmployeeWorkEntry"));
		assertTrue("WorkEntryApi must have adminUpdateWorkEntry", apiJs.contains("adminUpdateWorkEntry"));
		assertTrue("WorkEntryApi must have adminDeleteWorkEntry", apiJs.contains("adminDeleteWorkEntry"));

		File cssFile = new File(getWebappDir(), "assets/css/style.css");
		assertTrue("style.css must exist", cssFile.exists());
		String css = Files.readString(cssFile.toPath());

		// Verify datetime-local input is styled in form groups
		assertTrue("style.css must style input[type=\"datetime-local\"] in form-group",
				css.contains(".form-group input[type=\"datetime-local\"]"));

		// Verify calendar picker indicator styling
		assertTrue("style.css must define webkit calendar picker indicator styling",
				css.contains("input[type=\"datetime-local\"]::-webkit-calendar-picker-indicator") &&
						css.contains("cursor: pointer;"));
		assertTrue("style.css must define hover state for calendar picker indicator",
				css.contains("input[type=\"datetime-local\"]::-webkit-calendar-picker-indicator:hover"));
	}
}
