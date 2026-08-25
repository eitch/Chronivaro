package ch.eitchnet.chronivaro.rest;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class OpenApiSpecTest {

	@Test
	public void shouldContainCompleteOpenApiSpecification() throws IOException {
		File specFile = new File("../docs/openapi.yaml");
		if (!specFile.exists()) {
			specFile = new File("docs/openapi.yaml");
		}
		assertTrue("OpenAPI specification file docs/openapi.yaml must exist", specFile.exists());

		String content = Files.readString(specFile.toPath());
		assertTrue("OpenAPI version 3.0.3 must be specified", content.contains("openapi: 3.0.3"));
		assertTrue("Info section must contain Chronivaro REST API", content.contains("title: Chronivaro REST API"));

		// Verify standard schemas
		List<String> requiredSchemas = List.of(
				"ErrorDto:",
				"FieldErrorDto:",
				"VersionDto:",
				"PresenceDto:",
				"WorkEntryDto:",
				"DaySummaryDto:",
				"MonthSummaryDto:",
				"AbsenceDto:",
				"EmployeeDto:",
				"ScheduleDto:",
				"ScheduleTemplateDto:",
				"TeamDto:",
				"LocationDto:",
				"HolidayCalendarDto:",
				"HolidayDto:",
				"UserDto:",
				"AuditLogDto:",
				"ConfigurationDto:",
				"TeamReportDto:",
				"AbsenceReportDto:"
		);

		for (String schema : requiredSchemas) {
			assertTrue("OpenAPI spec must define schema: " + schema, content.contains(schema));
		}

		// Verify headers and parameters
		assertTrue("OpenAPI spec must define X-Correlation-Id header", content.contains("X-Correlation-Id:"));
		assertTrue("OpenAPI spec must define ETag header", content.contains("ETag:"));
		assertTrue("OpenAPI spec must define If-Match parameter/header", content.contains("IfMatchParam:"));
		assertTrue("OpenAPI spec must define OffsetParam", content.contains("OffsetParam:"));
		assertTrue("OpenAPI spec must define LimitParam", content.contains("LimitParam:"));

		// Verify key endpoints
		List<String> requiredPaths = List.of(
				"/chronivaro/v1/version:",
				"/chronivaro/v1/auth/login:",
				"/chronivaro/v1/auth/logout:",
				"/chronivaro/v1/auth/me:",
				"/chronivaro/v1/presence:",
				"/chronivaro/v1/presence/team/{teamId}:",
				"/chronivaro/v1/presence/employee/{employeeId}:",
				"/chronivaro/v1/timer/start:",
				"/chronivaro/v1/timer/stop:",
				"/chronivaro/v1/timer/status:",
				"/chronivaro/v1/work-entries:",
				"/chronivaro/v1/work-entries/day/{date}:",
				"/chronivaro/v1/work-entries/{id}:",
				"/chronivaro/v1/summary/day/{date}:",
				"/chronivaro/v1/summary/month/{yearMonth}:",
				"/chronivaro/v1/reports/day:",
				"/chronivaro/v1/reports/month:",
				"/chronivaro/v1/reports/vacation:",
				"/chronivaro/v1/reports/team:",
				"/chronivaro/v1/reports/absences:",
				"/chronivaro/v1/absences:",
				"/chronivaro/v1/absences/{id}:",
				"/chronivaro/v1/absences/{id}/approve:",
				"/chronivaro/v1/absences/{id}/reject:",
				"/chronivaro/v1/absences/{id}/cancel:",
				"/chronivaro/v1/periods/status:",
				"/chronivaro/v1/periods/submit:",
				"/chronivaro/v1/periods/approve:",
				"/chronivaro/v1/periods/reopen:",
				"/chronivaro/v1/periods/close-year:",
				"/chronivaro/v1/admin/employees:",
				"/chronivaro/v1/admin/employees/{id}:",
				"/chronivaro/v1/admin/employees/{id}/reactivate:",
				"/chronivaro/v1/admin/employees/{id}/schedules:",
				"/chronivaro/v1/admin/employees/{id}/vacation-account:",
				"/chronivaro/v1/admin/employees/{id}/vacation-correction:",
				"/chronivaro/v1/admin/teams:",
				"/chronivaro/v1/admin/teams/{id}:",
				"/chronivaro/v1/admin/locations:",
				"/chronivaro/v1/admin/locations/{id}:",
				"/chronivaro/v1/admin/absence-types:",
				"/chronivaro/v1/admin/absence-types/{id}:",
				"/chronivaro/v1/admin/holiday-calendars:",
				"/chronivaro/v1/admin/holiday-calendars/{id}:",
				"/chronivaro/v1/admin/holiday-calendars/{id}/holidays:",
				"/chronivaro/v1/admin/schedule-templates:",
				"/chronivaro/v1/admin/schedule-templates/{id}:",
				"/chronivaro/v1/admin/users:",
				"/chronivaro/v1/admin/users/{id}:",
				"/chronivaro/v1/admin/users/{id}/register:",
				"/chronivaro/v1/admin/audit-logs:",
				"/chronivaro/v1/admin/configuration:",
				"/chronivaro/v1/admin/corrections:"
		);

		for (String path : requiredPaths) {
			assertTrue("OpenAPI spec must document path: " + path, content.contains(path));
		}
	}
}
