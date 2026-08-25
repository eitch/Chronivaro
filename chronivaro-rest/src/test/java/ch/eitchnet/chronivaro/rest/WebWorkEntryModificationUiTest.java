package ch.eitchnet.chronivaro.rest;

import ch.eitchnet.chronivaro.rest.dto.WorkEntryDto;
import ch.eitchnet.chronivaro.rest.resource.ChronivaroRestHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class WebWorkEntryModificationUiTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldContainCompleteWebAssetsForWorkEntryModificationsAndHighlighting() throws IOException {
		File webDir = new File("../chronivaro-web/src/main/webapp");
		if (!webDir.exists()) {
			webDir = new File("chronivaro-web/src/main/webapp");
		}
		assertTrue("Web directory must exist", webDir.exists());

		File myTimesViewJs = new File(webDir, "js/pages/MyTimesView.js");
		assertTrue("MyTimesView.js must exist", myTimesViewJs.exists());
		String myTimesContent = Files.readString(myTimesViewJs.toPath());

		// Verify MyTimesView contains badges and creator attribution
		assertTrue("MyTimesView must render manualBadge", myTimesContent.contains("manualBadge"));
		assertTrue("MyTimesView must render modifiedBadge", myTimesContent.contains("modifiedBadge"));
		assertTrue("MyTimesView must render timerBadge", myTimesContent.contains("timerBadge"));
		assertTrue("MyTimesView must display creator attribution", myTimesContent.contains("times.createdBy"));

		// Verify MyTimesView does not enforce client-side shorten-only restrictions
		assertFalse("MyTimesView must not enforce shortenOnlyError on client", myTimesContent.contains("shortenOnlyError"));
		assertTrue("MyTimesView must allow start time editing", myTimesContent.contains("editStartInput.value = toLocalDateTimeInputString"));

		// Verify ApprovalsView contains badges for work entries
		File approvalsViewJs = new File(webDir, "js/pages/ApprovalsView.js");
		assertTrue("ApprovalsView.js must exist", approvalsViewJs.exists());
		String approvalsContent = Files.readString(approvalsViewJs.toPath());
		assertTrue("ApprovalsView must render manual badge for work entries", approvalsContent.contains("times.manualBadge"));
		assertTrue("ApprovalsView must render modified badge for work entries", approvalsContent.contains("times.modifiedBadge"));
		assertTrue("ApprovalsView must render creator info", approvalsContent.contains("times.createdBy"));

		// Verify ReportsView contains badges and creator column
		File reportsViewJs = new File(webDir, "js/pages/ReportsView.js");
		assertTrue("ReportsView.js must exist", reportsViewJs.exists());
		String reportsContent = Files.readString(reportsViewJs.toPath());
		assertTrue("ReportsView must render manualBadge", reportsContent.contains("times.manualBadge"));
		assertTrue("ReportsView must render modifiedBadge", reportsContent.contains("times.modifiedBadge"));
		assertTrue("ReportsView must render source and modified headers", reportsContent.contains("times.source") && reportsContent.contains("times.modified"));
	}

	@Test
	public void shouldHaveCompleteI18nKeyParityWithoutGermanEszett() throws IOException {
		File webDir = new File("../chronivaro-web/src/main/webapp");
		if (!webDir.exists()) {
			webDir = new File("chronivaro-web/src/main/webapp");
		}
		assertTrue("Web directory must exist", webDir.exists());

		File enJsonFile = new File(webDir, "i18n/en.json");
		File deJsonFile = new File(webDir, "i18n/de.json");
		assertTrue("en.json must exist", enJsonFile.exists());
		assertTrue("de.json must exist", deJsonFile.exists());

		String enContent = Files.readString(enJsonFile.toPath());
		String deContent = Files.readString(deJsonFile.toPath());

		// Swiss German check: no 'ß' allowed
		assertFalse("German localization must use Swiss German without 'ß' character", deContent.contains("ß"));

		JsonObject enObj = JsonParser.parseString(enContent).getAsJsonObject();
		JsonObject deObj = JsonParser.parseString(deContent).getAsJsonObject();

		Set<String> enKeys = extractAllKeys(enObj, "");
		Set<String> deKeys = extractAllKeys(deObj, "");

		assertEquals("Translation keys must match between en.json and de.json", enKeys, deKeys);
		assertTrue("en.json must contain times.manualBadge", enKeys.contains("times.manualBadge"));
		assertTrue("en.json must contain times.modifiedBadge", enKeys.contains("times.modifiedBadge"));
		assertTrue("en.json must contain times.createdBy", enKeys.contains("times.createdBy"));
	}

	private Set<String> extractAllKeys(JsonObject obj, String prefix) {
		Set<String> keys = new HashSet<>();
		for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
			String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
			if (entry.getValue().isJsonObject()) {
				keys.addAll(extractAllKeys(entry.getValue().getAsJsonObject(), key));
			} else {
				keys.add(key);
			}
		}
		return keys;
	}

	@Test
	public void shouldAllowEmployeeToFullyEditWorkEntryAndReflectModifiedStatus() {
		String employeeToken = authenticate("employee", "admin");

		// 1. Employee starts timer
		String startJson = """
				{
				  "workingLocation": "OFFICE"
				}
				""";
		try (Response startRes = target().path("/chronivaro/v1/me/timer/start")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.post(Entity.json(startJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), startRes.getStatus());
		}

		// 2. Employee stops timer
		String stopJson = """
				{
				  "comment": "Initial timer work"
				}
				""";
		try (Response stopRes = target().path("/chronivaro/v1/me/timer/stop")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.post(Entity.json(stopJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), stopRes.getStatus());
		}

		// 3. Employee queries own work entries
		String entryId;
		try (Response entriesRes = target().path("/chronivaro/v1/me/work-entries")
				.queryParam("from", "2026-08-01")
				.queryParam("to", "2026-08-31")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), entriesRes.getStatus());
			String jsonStr = entriesRes.readEntity(String.class);
			WorkEntryDto[] dtos = ChronivaroRestHelper.createGson().fromJson(jsonStr, WorkEntryDto[].class);
			assertTrue(dtos.length > 0);
			WorkEntryDto latest = dtos[dtos.length - 1];
			entryId = latest.id();
			assertEquals("TIMER", latest.source());
			assertEquals("employee", latest.createdBy());
			assertFalse("Unmodified timer entry must have modified = false", latest.modified());
		}

		// 4. Employee modifies their own work entry: adjusting start time, end time, location, comment
		String updateJson = """
				{
				  "start": "2026-08-24T08:00:00+02:00",
				  "end": "2026-08-24T17:00:00+02:00",
				  "workingLocation": "HOME_OFFICE",
				  "comment": "Adjusted by employee directly"
				}
				""";
		try (Response updateRes = target().path("/chronivaro/v1/me/work-entries/" + entryId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.put(Entity.json(updateJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), updateRes.getStatus());
			WorkEntryDto updated = ChronivaroRestHelper.createGson().fromJson(updateRes.readEntity(String.class), WorkEntryDto.class);
			assertEquals(entryId, updated.id());
			assertEquals("TIMER", updated.source());
			assertEquals("employee", updated.createdBy());
			assertTrue("Modified work entry must have modified = true", updated.modified());
			assertEquals("Adjusted by employee directly", updated.comment());
			assertEquals("HOME_OFFICE", updated.workingLocation().name());
		}
	}
}
