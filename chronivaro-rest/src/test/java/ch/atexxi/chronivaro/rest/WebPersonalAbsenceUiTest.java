package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.rest.dto.AbsenceDto;
import ch.atexxi.chronivaro.rest.dto.VacationAccountSummaryDto;
import ch.atexxi.chronivaro.rest.resource.ChronivaroRestHelper;
import com.google.gson.reflect.TypeToken;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class WebPersonalAbsenceUiTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldContainCompleteWebAssetsForPersonalAbsencesAndVacation() throws IOException {
		File webDir = new File("../chronivaro-web/src/main/webapp");
		if (!webDir.exists()) {
			webDir = new File("chronivaro-web/src/main/webapp");
		}
		assertTrue("Web directory must exist", webDir.exists());

		File indexHtml = new File(webDir, "index.html");
		assertTrue("index.html must exist", indexHtml.exists());
		String indexContent = Files.readString(indexHtml.toPath());
		assertTrue("index.html must have my-absences nav link", indexContent.contains("href=\"#my-absences\""));

		File appJs = new File(webDir, "js/app.js");
		assertTrue("app.js must exist", appJs.exists());
		String appJsContent = Files.readString(appJs.toPath());
		assertTrue("app.js must import MyAbsencesView", appJsContent.contains("MyAbsencesView"));
		assertTrue("app.js must route to my-absences", appJsContent.contains("case 'my-absences':"));

		File absenceApiJs = new File(webDir, "js/api/AbsenceApi.js");
		assertTrue("AbsenceApi.js must exist", absenceApiJs.exists());
		String absenceApiContent = Files.readString(absenceApiJs.toPath());
		assertTrue("AbsenceApi must provide getMyAbsences", absenceApiContent.contains("getMyAbsences"));
		assertTrue("AbsenceApi must provide requestAbsence", absenceApiContent.contains("requestAbsence"));
		assertTrue("AbsenceApi must provide cancelAbsence", absenceApiContent.contains("cancelAbsence"));

		File vacationApiJs = new File(webDir, "js/api/VacationAccountApi.js");
		assertTrue("VacationAccountApi.js must exist", vacationApiJs.exists());
		String vacationApiContent = Files.readString(vacationApiJs.toPath());
		assertTrue("VacationAccountApi must provide getMyVacationAccount", vacationApiContent.contains("getMyVacationAccount"));

		File myAbsencesViewJs = new File(webDir, "js/pages/MyAbsencesView.js");
		assertTrue("MyAbsencesView.js must exist", myAbsencesViewJs.exists());
		String viewContent = Files.readString(myAbsencesViewJs.toPath());
		assertTrue("View must contain vacation summary cards", viewContent.contains("vacation-cards-grid"));
		assertTrue("View must contain vacation journal details", viewContent.contains("vacation-journal-details"));
		assertTrue("View must contain absence filter controls", viewContent.contains("filter-controls"));
		assertTrue("View must contain absence table", viewContent.contains("absences-table"));
		assertTrue("View must contain absence request modal", viewContent.contains("absence-modal"));
	}

	@Test
	public void shouldExecutePersonalAbsenceAndVacationWorkflow() {
		String authToken = authenticate("admin", "admin");

		// 1. Fetch Vacation Account
		try (Response vacationRes = target().path("/chronivaro/v1/me/vacation-account")
				.queryParam("year", "2025")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), vacationRes.getStatus());
			VacationAccountSummaryDto vacationDto = ChronivaroRestHelper.createGson().fromJson(
					vacationRes.readEntity(String.class), VacationAccountSummaryDto.class);
			assertNotNull("Vacation account summary must not be null", vacationDto);
			assertEquals(2025, vacationDto.year());
		}

		// 2. Fetch Absence Types
		try (Response typesRes = target().path("/chronivaro/v1/admin/absence-types")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), typesRes.getStatus());
		}

		// 3. Request a new personal absence
		ZonedDateTime start = ZonedDateTime.parse("2025-07-10T00:00:00.000+01:00");
		ZonedDateTime end = ZonedDateTime.parse("2025-07-11T23:59:59.999+01:00");
		AbsenceDto newAbsence = new AbsenceDto(
				null,
				null,
				"VACATION",
				start,
				end,
				"FULL_DAY",
				null,
				null,
				"Summer Holiday request",
				null
		);

		String etag;
		AbsenceDto createdDto;
		try (Response createRes = target().path("/chronivaro/v1/me/absences")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(ChronivaroRestHelper.createGson().toJson(newAbsence)))) {
			assertEquals(Response.Status.OK.getStatusCode(), createRes.getStatus());
			createdDto = ChronivaroRestHelper.createGson().fromJson(
					createRes.readEntity(String.class), AbsenceDto.class);
			assertNotNull("Created absence ID must not be null", createdDto.id());
			assertEquals("SUBMITTED", createdDto.state());
			etag = createRes.getHeaderString("ETag");
			assertNotNull("Created response must have ETag", etag);
		}

		// 4. Query personal absences with filters
		try (Response listRes = target().path("/chronivaro/v1/me/absences")
				.queryParam("from", "2025-01-01")
				.queryParam("to", "2025-12-31")
				.queryParam("status", "SUBMITTED")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), listRes.getStatus());
			Type listType = new TypeToken<List<AbsenceDto>>() {}.getType();
			List<AbsenceDto> listResult = ChronivaroRestHelper.createGson().fromJson(
					listRes.readEntity(String.class), listType);
			assertNotNull("List result must not be null", listResult);
			assertFalse("List result must contain the created absence", listResult.isEmpty());
		}

		// 5. Cancel the requested absence
		try (Response cancelRes = target().path("/chronivaro/v1/me/absences/" + createdDto.id() + "/cancel")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.header("If-Match", etag)
				.post(Entity.json("{\"reason\":\"Cancelled due to changed plans\"}"))) {
			assertEquals(Response.Status.OK.getStatusCode(), cancelRes.getStatus());
			AbsenceDto cancelledDto = ChronivaroRestHelper.createGson().fromJson(
					cancelRes.readEntity(String.class), AbsenceDto.class);
			assertEquals("CANCELLED", cancelledDto.state());
		}
	}
}
