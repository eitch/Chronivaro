package ch.eitchnet.chronivaro.web;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class WebDatePickerUiTest {

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
	public void shouldVerifyDatePickerUtilityImplementation() throws IOException {
		File pickerFile = new File(getWebappDir(), "js/utils/DatePicker.js");
		assertTrue("DatePicker.js must exist", pickerFile.exists());
		String js = Files.readString(pickerFile.toPath());

		// Feature detection & attachment
		assertTrue("DatePicker must have isDateInputSupported", js.contains("function isDateInputSupported()"));
		assertTrue("DatePicker must support attachDatePicker", js.contains("function attachDatePicker("));

		// Popover rendering and interaction
		assertTrue("DatePicker must create .date-picker-wrapper", js.contains("date-picker-wrapper"));
		assertTrue("DatePicker must create .date-picker-popover", js.contains("date-picker-popover"));
		assertTrue("DatePicker must create .date-picker-toggle", js.contains("date-picker-toggle"));
		assertTrue("DatePicker must support year and month navigation",
				js.contains("prev-year") && js.contains("next-year") && js.contains("prev-month") && js.contains("next-month"));
		assertTrue("DatePicker must support day selection buttons", js.contains("date-picker-day-btn"));
		assertTrue("DatePicker must support today and clear buttons", js.contains("date-picker-today-btn") && js.contains("date-picker-clear-btn"));
		assertTrue("DatePicker must trigger input & change events",
				js.contains("new Event('input', { bubbles: true })") && js.contains("new Event('change', { bubbles: true })"));

		// Observer / automatic initialization
		assertTrue("DatePicker must support init and initAll", js.contains("initDatePickers(") && js.contains("initAllDatePickers("));
	}

	@Test
	public void shouldVerifyDatePickerIntegrationInAppAndViews() throws IOException {
		File appFile = new File(getWebappDir(), "js/app.js");
		assertTrue("app.js must exist", appFile.exists());
		String appJs = Files.readString(appFile.toPath());
		assertTrue("app.js must import DatePicker", appJs.contains("import DatePicker from './utils/DatePicker.js';"));
		assertTrue("app.js must call DatePicker.initAll()", appJs.contains("DatePicker.initAll();"));

		File reportsViewFile = new File(getWebappDir(), "js/pages/ReportsView.js");
		assertTrue("ReportsView.js must exist", reportsViewFile.exists());
		String reportsJs = Files.readString(reportsViewFile.toPath());
		assertTrue("ReportsView must import DatePicker", reportsJs.contains("import DatePicker from '../utils/DatePicker.js';"));
		assertTrue("ReportsView must initialize DatePicker", reportsJs.contains("DatePicker.init(this.filterBar)"));

		File approvalsViewFile = new File(getWebappDir(), "js/pages/ApprovalsView.js");
		assertTrue("ApprovalsView.js must exist", approvalsViewFile.exists());
		String approvalsJs = Files.readString(approvalsViewFile.toPath());
		assertTrue("ApprovalsView must import DatePicker", approvalsJs.contains("import DatePicker from '../utils/DatePicker.js';"));
		assertTrue("ApprovalsView must initialize DatePicker", approvalsJs.contains("DatePicker.init(container)"));
	}

	@Test
	public void shouldVerifyDatePickerStylesInCss() throws IOException {
		File cssFile = new File(getWebappDir(), "assets/css/style.css");
		assertTrue("style.css must exist", cssFile.exists());
		String css = Files.readString(cssFile.toPath());

		assertTrue("style.css must contain .date-picker-wrapper", css.contains(".date-picker-wrapper"));
		assertTrue("style.css must contain .date-picker-toggle", css.contains(".date-picker-toggle"));
		assertTrue("style.css must contain .date-picker-popover", css.contains(".date-picker-popover"));
		assertTrue("style.css must style .date-picker-popover with fixed positioning so modal dialogs are not resized",
				css.contains(".date-picker-popover {\n    position: fixed;"));
		assertTrue("style.css must contain .date-picker-day-btn", css.contains(".date-picker-day-btn"));
		assertTrue("style.css must keep toggle transform on hover/focus/active to prevent jumping",
				css.contains(".date-picker-toggle:hover") && css.contains("transform: translateY(-50%);"));
	}

	@Test
	public void shouldVerifyDatePickerI18nKeys() throws IOException {
		File deFile = new File(getWebappDir(), "i18n/de.json");
		File enFile = new File(getWebappDir(), "i18n/en.json");
		assertTrue("de.json must exist", deFile.exists());
		assertTrue("en.json must exist", enFile.exists());

		String deJson = Files.readString(deFile.toPath());
		String enJson = Files.readString(enFile.toPath());

		assertTrue("de.json must contain chooseDate", deJson.contains("\"chooseDate\""));
		assertTrue("en.json must contain chooseDate", enJson.contains("\"chooseDate\""));
		assertTrue("de.json must contain prevMonth", deJson.contains("\"prevMonth\""));
		assertTrue("en.json must contain prevMonth", enJson.contains("\"prevMonth\""));
		assertTrue("de.json must contain nextMonth", deJson.contains("\"nextMonth\""));
		assertTrue("en.json must contain nextMonth", enJson.contains("\"nextMonth\""));
		assertTrue("de.json must contain shortWeekdays", deJson.contains("\"shortWeekdays\""));
		assertTrue("en.json must contain shortWeekdays", enJson.contains("\"shortWeekdays\""));
	}
}
