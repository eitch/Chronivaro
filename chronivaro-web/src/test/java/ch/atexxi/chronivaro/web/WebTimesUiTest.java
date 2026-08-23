package ch.atexxi.chronivaro.web;

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
