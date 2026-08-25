package ch.eitchnet.chronivaro.web;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class WebEmployeesUiTest {

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
	public void shouldVerifyEmployeesViewStatusFilterAndBadgeRendering() throws IOException {
		File viewFile = new File(getWebappDir(), "js/pages/EmployeesView.js");
		assertTrue("EmployeesView.js must exist", viewFile.exists());
		String viewJs = Files.readString(viewFile.toPath());

		// Verify status filter controls
		assertTrue("EmployeesView must contain employee-status-filter select",
				viewJs.contains("id=\"employee-status-filter\""));
		assertTrue("EmployeesView must contain employee-search-filter input",
				viewJs.contains("id=\"employee-search-filter\""));
		assertTrue("EmployeesView must contain employee-filter-reset-btn button",
				viewJs.contains("id=\"employee-filter-reset-btn\""));

		// Verify status filter options (all, active, inactive)
		assertTrue("EmployeesView must support 'all' status option", viewJs.contains("value=\"all\""));
		assertTrue("EmployeesView must support 'active' status option", viewJs.contains("value=\"active\""));
		assertTrue("EmployeesView must support 'inactive' status option", viewJs.contains("value=\"inactive\""));

		// Verify status badges and row highlight
		assertTrue("EmployeesView must render active status badge", viewJs.contains("status-badge badge-active"));
		assertTrue("EmployeesView must render inactive status badge", viewJs.contains("status-badge badge-inactive"));
		assertTrue("EmployeesView must apply inactive-row CSS class for inactive employees",
				viewJs.contains("row.classList.add('inactive-row')"));

		// Verify reactivate button for inactive employees
		assertTrue("EmployeesView must render reactivate button for inactive employees",
				viewJs.contains("reactivate-btn"));
		assertTrue("EmployeesView must call EmployeeApi.reactivate",
				viewJs.contains("EmployeeApi.reactivate(id)"));
	}

	@Test
	public void shouldVerifyEmployeesCssBadgeAndHighlightRules() throws IOException {
		File cssFile = new File(getWebappDir(), "assets/css/style.css");
		assertTrue("style.css must exist", cssFile.exists());
		String css = Files.readString(cssFile.toPath());

		// Verify badge and row styling
		assertTrue("style.css must define .status-badge styling",
				css.contains(".status-badge") && css.contains("padding: 0.25rem 0.625rem;"));
		assertTrue("style.css must define .badge-active styling",
				css.contains(".badge-active") && css.contains("background-color: #dcfce7"));
		assertTrue("style.css must define .badge-inactive styling",
				css.contains(".badge-inactive") && css.contains("background-color: #fee2e2"));
		assertTrue("style.css must define tr.inactive-row styling",
				css.contains("tr.inactive-row") && css.contains("background-color: #f8fafc"));

		// Verify table container and dropdown overflow styling
		assertTrue("style.css must define .table-container overflow handling",
				css.contains(".table-container"));
		assertTrue("style.css must ensure .table-container:has(.dropdown.show) has overflow: visible",
				css.contains(".table-container:has(.dropdown.show)") && css.contains("overflow: visible;"));
		assertTrue("style.css must ensure td:has(.dropdown) has overflow: visible",
				css.contains("td:has(.dropdown)") && css.contains("overflow: visible;"));
	}

	@Test
	public void shouldVerifyEmployeesActionsDropdownAndContainerVisibility() throws IOException {
		File viewFile = new File(getWebappDir(), "js/pages/EmployeesView.js");
		assertTrue("EmployeesView.js must exist", viewFile.exists());
		String viewJs = Files.readString(viewFile.toPath());

		// Verify actions dropdown markup and container overflow visibility
		assertTrue("EmployeesView must define dropdown markup", viewJs.contains("class=\"dropdown\""));
		assertTrue("EmployeesView must define dropdown-toggle button", viewJs.contains("class=\"ghost dropdown-toggle\""));
		assertTrue("EmployeesView must define dropdown-content container", viewJs.contains("class=\"dropdown-content\""));
		assertTrue("EmployeesView table-container card must not force overflow-x: auto inline",
				!viewJs.contains("<div class=\"table-container card\" style=\"padding: 1rem; overflow-x: auto;\">"));
	}

	@Test
	public void shouldVerifyEmployeeFormValidationRules() throws IOException {
		File viewFile = new File(getWebappDir(), "js/pages/EmployeesView.js");
		assertTrue("EmployeesView.js must exist", viewFile.exists());
		String viewJs = Files.readString(viewFile.toPath());

		// Verify optional / hidden fields do not have required attribute preventing form submission when hidden
		assertTrue("sched-template select must not have required attribute",
				!viewJs.contains("id=\"sched-template\" required"));
		assertTrue("emp-id input must not have required attribute",
				!viewJs.contains("id=\"emp-id\" required"));
	}
}
