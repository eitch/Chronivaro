package ch.eitchnet.chronivaro.web;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class WebAbsenceCalendarUiTest {

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
	public void shouldVerifyAbsenceCalendarViewImplementation() throws IOException {
		File viewFile = new File(getWebappDir(), "js/pages/AbsenceCalendarView.js");
		assertTrue("AbsenceCalendarView.js must exist", viewFile.exists());
		String js = Files.readString(viewFile.toPath());

		// Verify view initialization and class export
		assertTrue("Must export default AbsenceCalendarView", js.contains("export default class AbsenceCalendarView"));
		assertTrue("Must support timeline view mode", js.contains("this.viewMode = 'timeline'"));

		// Verify navigation & month picker
		assertTrue("Must implement loadCalendarData", js.contains("loadCalendarData("));
		assertTrue("Must implement refreshMonthPicker", js.contains("refreshMonthPicker("));

		// Verify filtering support
		assertTrue("Must contain team filter selector", js.contains("#cal-filter-team"));
		assertTrue("Must contain location filter selector", js.contains("#cal-filter-location"));
		assertTrue("Must contain employee filter selector", js.contains("#cal-filter-employee"));
		assertTrue("Must contain absence type filter selector", js.contains("#cal-filter-type"));

		// Verify timeline and month grid rendering methods
		assertTrue("Must implement renderTimelineView", js.contains("renderTimelineView("));
		assertTrue("Must implement renderMonthGridView", js.contains("renderMonthGridView("));

		// Verify direct absence creation from calendar
		assertTrue("Must implement openCreateAbsenceModal", js.contains("openCreateAbsenceModal("));
		assertTrue("Must support cell click direct creation", js.contains("this.openCreateAbsenceModal(container, { employeeId: empId, startDate: dateStr, endDate: dateStr });"));
		assertTrue("Must support manager direct approval", js.contains("modal-direct-approval"));

		// Verify modal details inspection
		assertTrue("Must implement openAbsenceDetailsModal", js.contains("openAbsenceDetailsModal("));
	}

	@Test
	public void shouldVerifyNavigationIntegration() throws IOException {
		File indexHtml = new File(getWebappDir(), "index.html");
		assertTrue("index.html must exist", indexHtml.exists());
		String html = Files.readString(indexHtml.toPath());

		assertTrue("index.html must contain #absence-calendar navigation link",
				html.contains("href=\"#absence-calendar\""));
		assertTrue("absence-calendar link must have i18n key nav.absenceCalendar",
				html.contains("data-i18n=\"nav.absenceCalendar\""));

		File appJsFile = new File(getWebappDir(), "js/app.js");
		assertTrue("app.js must exist", appJsFile.exists());
		String appJs = Files.readString(appJsFile.toPath());

		assertTrue("app.js must import AbsenceCalendarView",
				appJs.contains("import AbsenceCalendarView from './pages/AbsenceCalendarView.js'"));
		assertTrue("app.js must route case 'absence-calendar'",
				appJs.contains("case 'absence-calendar':"));
	}

	@Test
	public void shouldVerifyCalendarStyling() throws IOException {
		File cssFile = new File(getWebappDir(), "assets/css/style.css");
		assertTrue("style.css must exist", cssFile.exists());
		String css = Files.readString(cssFile.toPath());

		assertTrue("style.css must define .absence-calendar-container",
				css.contains(".absence-calendar-container"));
		assertTrue("style.css must define .calendar-timeline-table",
				css.contains(".calendar-timeline-table"));
		assertTrue("style.css must define .cal-col-emp with position sticky",
				css.contains("position: sticky;"));
		assertTrue("style.css must define .cal-badge",
				css.contains(".cal-badge"));
		assertTrue("style.css must define .type-vacation",
				css.contains(".type-vacation"));
		assertTrue("style.css must define .type-illness",
				css.contains(".type-illness"));
		assertTrue("style.css must define .badge-submitted",
				css.contains(".badge-submitted"));
	}
}
