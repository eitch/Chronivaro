package ch.eitchnet.chronivaro.web;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class WebPresenceUiTest {

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
	public void shouldVerifyPresenceDashboardRowLayoutAndCardSpacing() throws IOException {
		File viewFile = new File(getWebappDir(), "js/pages/PresenceView.js");
		assertTrue("PresenceView.js must exist", viewFile.exists());
		String viewJs = Files.readString(viewFile.toPath());

		// Verify that presence cards are grouped in presence-cards-grid container
		assertTrue("PresenceView must create presence-cards-grid container for employee cards",
				viewJs.contains("cardsContainer.className = 'presence-cards-grid';"));
		assertTrue("PresenceView must append cards to cardsContainer",
				viewJs.contains("cardsContainer.appendChild(card);"));
		assertTrue("PresenceView must append cardsContainer to teamGroup",
				viewJs.contains("teamGroup.appendChild(cardsContainer);"));

		File cssFile = new File(getWebappDir(), "assets/css/style.css");
		assertTrue("style.css must exist", cssFile.exists());
		String css = Files.readString(cssFile.toPath());

		// Verify presence-list layout
		assertTrue("style.css must define .presence-list with flex display",
				css.contains(".presence-list") && css.contains("display: flex;"));

		// Verify presence-cards-grid horizontal flex row wrapping layout
		assertTrue("style.css must define .presence-cards-grid with flex display",
				css.contains(".presence-cards-grid") && css.contains("display: flex;"));
		assertTrue("style.css must define flex-direction: row for presence cards grid",
				css.contains(".presence-cards-grid") && css.contains("flex-direction: row;"));
		assertTrue("style.css must define flex-wrap: wrap for presence cards grid",
				css.contains(".presence-cards-grid") && css.contains("flex-wrap: wrap;"));
		assertTrue("style.css must define gap spacing for presence cards grid",
				css.contains(".presence-cards-grid") && css.contains("gap: 1.5rem;"));

		// Verify presence-card dimensions and spacing
		assertTrue("style.css must define flex growth/basis for presence cards",
				css.contains(".presence-card") && css.contains("flex: 1 1 280px;"));
		assertTrue("style.css must define min-width for presence cards",
				css.contains(".presence-card") && css.contains("min-width: 260px;"));
		assertTrue("style.css must define padding for presence cards",
				css.contains(".presence-card") && css.contains("padding: 1.5rem;"));
	}
}
