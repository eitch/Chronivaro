package ch.eitchnet.chronivaro.web;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class WebMonthPickerUiTest {

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
	public void shouldVerifyMonthPickerUtilityImplementation() throws IOException {
		File pickerFile = new File(getWebappDir(), "js/utils/MonthPicker.js");
		assertTrue("MonthPicker.js must exist", pickerFile.exists());
		String js = Files.readString(pickerFile.toPath());

		// Feature detection
		assertTrue("MonthPicker must have isMonthInputSupported", js.contains("function isMonthInputSupported()"));

		// Popover rendering and interaction
		assertTrue("MonthPicker must create .month-picker-wrapper", js.contains("month-picker-wrapper"));
		assertTrue("MonthPicker must create .month-picker-popover", js.contains("month-picker-popover"));
		assertTrue("MonthPicker must create .month-picker-toggle", js.contains("month-picker-toggle"));
		assertTrue("MonthPicker must support prev/next year navigation", js.contains("prev-year") && js.contains("next-year"));
		assertTrue("MonthPicker must support month selection buttons", js.contains("month-picker-month-btn"));
		assertTrue("MonthPicker must trigger input & change events",
				js.contains("new Event('input', { bubbles: true })") && js.contains("new Event('change', { bubbles: true })"));

		// Observer / automatic initialization
		assertTrue("MonthPicker must support init and initAll", js.contains("initMonthPickers(") && js.contains("initAllMonthPickers("));
	}

	@Test
	public void shouldVerifyMonthPickerIntegrationInViews() throws IOException {
		File reportsViewFile = new File(getWebappDir(), "js/pages/ReportsView.js");
		assertTrue("ReportsView.js must exist", reportsViewFile.exists());
		String reportsJs = Files.readString(reportsViewFile.toPath());
		assertTrue("ReportsView must import MonthPicker", reportsJs.contains("import MonthPicker from '../utils/MonthPicker.js';"));
		assertTrue("ReportsView must initialize MonthPicker", reportsJs.contains("MonthPicker.init(this.filterBar)"));

		File approvalsViewFile = new File(getWebappDir(), "js/pages/ApprovalsView.js");
		assertTrue("ApprovalsView.js must exist", approvalsViewFile.exists());
		String approvalsJs = Files.readString(approvalsViewFile.toPath());
		assertTrue("ApprovalsView must import MonthPicker", approvalsJs.contains("import MonthPicker from '../utils/MonthPicker.js';"));
		assertTrue("ApprovalsView must initialize MonthPicker", approvalsJs.contains("MonthPicker.init(container)"));

		File myPeriodsViewFile = new File(getWebappDir(), "js/pages/MyPeriodsView.js");
		assertTrue("MyPeriodsView.js must exist", myPeriodsViewFile.exists());
		String periodsJs = Files.readString(myPeriodsViewFile.toPath());
		assertTrue("MyPeriodsView must import MonthPicker", periodsJs.contains("import MonthPicker from '../utils/MonthPicker.js';"));
		assertTrue("MyPeriodsView must initialize MonthPicker", periodsJs.contains("MonthPicker.init(container)"));

		File appFile = new File(getWebappDir(), "js/app.js");
		assertTrue("app.js must exist", appFile.exists());
		String appJs = Files.readString(appFile.toPath());
		assertTrue("app.js must import MonthPicker", appJs.contains("import MonthPicker from './utils/MonthPicker.js';"));
		assertTrue("app.js must call MonthPicker.initAll()", appJs.contains("MonthPicker.initAll();"));
	}

	@Test
	public void shouldVerifyMonthPickerStylesInCss() throws IOException {
		File cssFile = new File(getWebappDir(), "assets/css/style.css");
		assertTrue("style.css must exist", cssFile.exists());
		String css = Files.readString(cssFile.toPath());

		assertTrue("style.css must contain .month-picker-wrapper", css.contains(".month-picker-wrapper"));
		assertTrue("style.css must contain .month-picker-toggle", css.contains(".month-picker-toggle"));
		assertTrue("style.css must contain .month-picker-popover", css.contains(".month-picker-popover"));
		assertTrue("style.css must style .month-picker-popover with fixed positioning so modal dialogs are not resized",
				css.contains(".month-picker-popover {\n    position: fixed;"));
		assertTrue("style.css must contain .month-picker-month-btn", css.contains(".month-picker-month-btn"));
		assertTrue("style.css must keep toggle transform on hover/focus/active to prevent jumping",
				css.contains(".month-picker-toggle:hover") && css.contains("transform: translateY(-50%);"));
	}
}
