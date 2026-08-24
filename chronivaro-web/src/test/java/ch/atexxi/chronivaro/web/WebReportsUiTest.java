package ch.atexxi.chronivaro.web;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class WebReportsUiTest {

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
	public void shouldVerifyHierarchicalEmployeeSelectionAndDatePickersInReportsView() throws IOException {
		File viewFile = new File(getWebappDir(), "js/pages/ReportsView.js");
		assertTrue("ReportsView.js must exist", viewFile.exists());
		String viewJs = Files.readString(viewFile.toPath());

		// Verify API imports
		assertTrue("ReportsView must import EmployeeApi", viewJs.contains("import EmployeeApi from '../api/EmployeeApi.js';"));
		assertTrue("ReportsView must import TeamApi", viewJs.contains("import TeamApi from '../api/TeamApi.js';"));
		assertTrue("ReportsView must import ReportApi", viewJs.contains("import ReportApi from '../api/ReportApi.js';"));

		// Verify hierarchical team & employee selection in day report
		assertTrue("ReportsView must contain filter-day-team select", viewJs.contains("id=\"filter-day-team\""));
		assertTrue("ReportsView must contain filter-day-emp select", viewJs.contains("id=\"filter-day-emp\""));

		// Verify hierarchical team & employee selection and month picker in month report
		assertTrue("ReportsView must contain filter-month-team select", viewJs.contains("id=\"filter-month-team\""));
		assertTrue("ReportsView must contain filter-month-emp select", viewJs.contains("id=\"filter-month-emp\""));
		assertTrue("ReportsView must contain month picker input for Month report",
				viewJs.contains("type=\"month\" id=\"filter-month-ym\""));

		// Verify hierarchical team & employee selection in vacation report
		assertTrue("ReportsView must contain filter-vacation-team select", viewJs.contains("id=\"filter-vacation-team\""));
		assertTrue("ReportsView must contain filter-vacation-emp select", viewJs.contains("id=\"filter-vacation-emp\""));

		// Verify team selection dropdown in team report
		assertTrue("ReportsView must contain filter-team-id-select dropdown", viewJs.contains("id=\"filter-team-id-select\""));
		assertTrue("ReportsView must contain month picker input for Team report",
				viewJs.contains("type=\"month\" id=\"filter-team-ym\""));
		assertTrue("ReportsView must place month selection before team selection in Team report",
				viewJs.indexOf("id=\"filter-team-ym\"") < viewJs.indexOf("id=\"filter-team-id-select\""));

		// Verify hierarchical team & employee selection in absences report
		assertTrue("ReportsView must contain filter-absences-team select", viewJs.contains("id=\"filter-absences-team\""));
		assertTrue("ReportsView must contain filter-absences-emp select", viewJs.contains("id=\"filter-absences-emp\""));

		// Verify populate methods exist for hierarchical filtering
		assertTrue("ReportsView must implement populateEmployeeSelect", viewJs.contains("populateEmployeeSelect("));
		assertTrue("ReportsView must implement populateTeamSelect", viewJs.contains("populateTeamSelect("));
		assertTrue("ReportsView must filter employees by teamId in populateEmployeeSelect",
				viewJs.contains("e.teamId === selectedTeamId"));

		// Verify async loading of reference data including employees
		assertTrue("ReportsView must load employees via EmployeeApi.getAll()",
				viewJs.contains("EmployeeApi.getAll()"));

		// Verify vacation report displays username / personal number instead of raw internal employeeId
		assertTrue("ReportsView must format employee display with username/personal number in vacation report",
				viewJs.contains("username") && viewJs.contains("personalNumber") && viewJs.contains("empDisplay"));

		// Verify role-based visibility gating for team monthly overview
		assertTrue("ReportsView must define canViewTeamReport method", viewJs.contains("canViewTeamReport()"));
		assertTrue("ReportsView canViewTeamReport must check Supervisor role", viewJs.contains("AuthApi.hasRole('Supervisor')"));
		assertTrue("ReportsView canViewTeamReport must check HR role", viewJs.contains("AuthApi.hasRole('HR')"));
		assertTrue("ReportsView canViewTeamReport must check Administrator role", viewJs.contains("AuthApi.hasRole('Administrator')"));
		assertTrue("ReportsView must conditionally render team report tab based on canViewTeamReport",
				viewJs.contains("${this.canViewTeamReport() ? `") && viewJs.contains("id=\"report-type-team-btn\""));
		assertTrue("ReportsView must redirect unauthorized team report access in render",
				viewJs.contains("this.activeReportType === 'team' && !this.canViewTeamReport()"));
	}
}
