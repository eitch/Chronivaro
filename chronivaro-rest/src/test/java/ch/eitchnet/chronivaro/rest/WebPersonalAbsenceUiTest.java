package ch.eitchnet.chronivaro.rest;

import ch.eitchnet.chronivaro.rest.dto.AbsenceDto;
import ch.eitchnet.chronivaro.rest.dto.VacationAccountSummaryDto;
import ch.eitchnet.chronivaro.rest.resource.ChronivaroRestHelper;
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
		assertTrue("View must resolve absence state for status badge and label", viewContent.contains("absence.state || absence.status") || viewContent.contains("absence.state"));
		assertTrue("View must contain edit-btn for draft absences", viewContent.contains("edit-btn"));
		assertTrue("View must handle updateAbsence for draft editing", viewContent.contains("updateAbsence"));
		assertTrue("View must resolve vacation entry type from vacationType property",
				viewContent.contains("entry.vacationType") || viewContent.contains("entry.vacationType ||"));
		assertTrue("View must localize vacationEntryType",
				viewContent.contains("enums.vacationEntryType."));

		File styleCss = new File(webDir, "assets/css/style.css");
		assertTrue("style.css must exist", styleCss.exists());
		String styleContent = Files.readString(styleCss.toPath());
		assertTrue("style.css must contain .action-btn.submit-btn styling", styleContent.contains(".action-btn.submit-btn"));
		assertTrue("style.css must contain .action-btn.edit-btn styling", styleContent.contains(".action-btn.edit-btn"));
		assertTrue("style.css must contain .action-btn.cancel-btn styling", styleContent.contains(".action-btn.cancel-btn"));
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
			assertEquals("admin", vacationDto.username());
			assertNotNull("Employee name should be populated", vacationDto.employeeName());
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

	@Test
	public void shouldExecutePersonalDraftAbsenceWorkflow() {
		String authToken = authenticate("admin", "admin");

		// 1. Create a draft absence
		ZonedDateTime start = ZonedDateTime.parse("2025-08-10T00:00:00.000+01:00");
		ZonedDateTime end = ZonedDateTime.parse("2025-08-11T23:59:59.999+01:00");
		AbsenceDto draftAbsence = new AbsenceDto(
				null,
				null,
				"VACATION",
				start,
				end,
				"FULL_DAY",
				null,
				null,
				"Draft vacation",
				"DRAFT"
		);

		String draftEtag;
		AbsenceDto draftDto;
		try (Response createRes = target().path("/chronivaro/v1/me/absences")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(ChronivaroRestHelper.createGson().toJson(draftAbsence)))) {
			assertEquals(Response.Status.OK.getStatusCode(), createRes.getStatus());
			draftDto = ChronivaroRestHelper.createGson().fromJson(
					createRes.readEntity(String.class), AbsenceDto.class);
			assertNotNull("Created draft ID must not be null", draftDto.id());
			assertEquals("DRAFT", draftDto.state());
			draftEtag = createRes.getHeaderString("ETag");
			assertNotNull("Created draft must have ETag", draftEtag);
		}

		// 2. Update the draft absence
		AbsenceDto updateDto = new AbsenceDto(
				draftDto.id(),
				null,
				"VACATION",
				start,
				end,
				"FULL_DAY",
				null,
				null,
				"Updated draft comment",
				"DRAFT"
		);

		String updatedEtag;
		try (Response updateRes = target().path("/chronivaro/v1/me/absences/" + draftDto.id())
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.header("If-Match", draftEtag)
				.put(Entity.json(ChronivaroRestHelper.createGson().toJson(updateDto)))) {
			assertEquals(Response.Status.OK.getStatusCode(), updateRes.getStatus());
			AbsenceDto updatedResult = ChronivaroRestHelper.createGson().fromJson(
					updateRes.readEntity(String.class), AbsenceDto.class);
			assertEquals("Updated draft comment", updatedResult.comment());
			assertEquals("DRAFT", updatedResult.state());
			updatedEtag = updateRes.getHeaderString("ETag");
			assertNotNull("Updated response must have ETag", updatedEtag);
		}

		// 3. Submit the updated draft absence
		try (Response submitRes = target().path("/chronivaro/v1/me/absences/" + draftDto.id() + "/submit")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.header("If-Match", updatedEtag)
				.post(Entity.json("{}"))) {
			assertEquals(Response.Status.OK.getStatusCode(), submitRes.getStatus());
			AbsenceDto submittedDto = ChronivaroRestHelper.createGson().fromJson(
					submitRes.readEntity(String.class), AbsenceDto.class);
			assertEquals("SUBMITTED", submittedDto.state());
		}

		// 4. Create another draft and cancel it directly
		AbsenceDto secondDraft = new AbsenceDto(
				null,
				null,
				"VACATION",
				ZonedDateTime.parse("2025-08-15T00:00:00.000+01:00"),
				ZonedDateTime.parse("2025-08-16T23:59:59.999+01:00"),
				"FULL_DAY",
				null,
				null,
				"To be cancelled draft",
				"DRAFT"
		);

		String secondDraftEtag;
		AbsenceDto secondDraftDto;
		try (Response createRes = target().path("/chronivaro/v1/me/absences")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(ChronivaroRestHelper.createGson().toJson(secondDraft)))) {
			assertEquals(Response.Status.OK.getStatusCode(), createRes.getStatus());
			secondDraftDto = ChronivaroRestHelper.createGson().fromJson(
					createRes.readEntity(String.class), AbsenceDto.class);
			assertEquals("DRAFT", secondDraftDto.state());
			secondDraftEtag = createRes.getHeaderString("ETag");
		}

		try (Response cancelRes = target().path("/chronivaro/v1/me/absences/" + secondDraftDto.id() + "/cancel")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.header("If-Match", secondDraftEtag)
				.post(Entity.json("{\"reason\":\"Discard draft\"}"))) {
			assertEquals(Response.Status.OK.getStatusCode(), cancelRes.getStatus());
			AbsenceDto cancelledDto = ChronivaroRestHelper.createGson().fromJson(
					cancelRes.readEntity(String.class), AbsenceDto.class);
			assertEquals("CANCELLED", cancelledDto.state());
		}
	}
}
