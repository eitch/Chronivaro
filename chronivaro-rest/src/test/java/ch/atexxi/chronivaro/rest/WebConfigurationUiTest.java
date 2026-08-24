package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.rest.dto.ConfigurationDto;
import ch.atexxi.chronivaro.rest.resource.ChronivaroRestHelper;
import com.google.gson.Gson;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class WebConfigurationUiTest extends AbstractChronivaroRestfulTest {

	private static final Gson gson = ChronivaroRestHelper.createGson();

	@Test
	public void shouldVerifyWebConfigurationAssetsAndRoutes() throws IOException {
		File webappDir = new File("../chronivaro-web/src/main/webapp");
		if (!webappDir.exists()) {
			webappDir = new File("chronivaro-web/src/main/webapp");
		}
		assertTrue("Webapp directory must exist", webappDir.exists());

		// 1. Verify ConfigurationApi.js
		File apiFile = new File(webappDir, "js/api/ConfigurationApi.js");
		assertTrue("ConfigurationApi.js must exist", apiFile.exists());
		String apiContent = Files.readString(apiFile.toPath());
		assertTrue("ConfigurationApi must export class", apiContent.contains("export default class ConfigurationApi"));
		assertTrue("ConfigurationApi must define getBranding", apiContent.contains("getBranding"));
		assertTrue("ConfigurationApi must define getConfiguration", apiContent.contains("getConfiguration"));
		assertTrue("ConfigurationApi must define updateConfiguration", apiContent.contains("updateConfiguration"));
		assertTrue("ConfigurationApi must send If-Match header", apiContent.contains("If-Match"));

		// 2. Verify ConfigurationView.js
		File viewFile = new File(webappDir, "js/pages/ConfigurationView.js");
		assertTrue("ConfigurationView.js must exist", viewFile.exists());
		String viewContent = Files.readString(viewFile.toPath());
		assertTrue("ConfigurationView must export class", viewContent.contains("export default class ConfigurationView"));
		assertTrue("ConfigurationView must define render method", viewContent.contains("async render()"));
		assertTrue("ConfigurationView must contain configuration-view ID",
				viewContent.contains("configuration-view"));
		assertTrue("ConfigurationView must contain configuration-form ID", viewContent.contains("id=\"configuration-form\""));
		assertTrue("ConfigurationView must contain config-company-name ID", viewContent.contains("id=\"config-company-name\""));
		assertTrue("ConfigurationView must contain config-company-logo ID", viewContent.contains("id=\"config-company-logo\""));
		assertTrue("ConfigurationView must contain config-default-language ID", viewContent.contains("id=\"config-default-language\""));
		assertTrue("ConfigurationView must contain config-weekly-target ID", viewContent.contains("id=\"config-weekly-target\""));
		assertTrue("ConfigurationView must contain config-vacation-days ID", viewContent.contains("id=\"config-vacation-days\""));
		assertTrue("ConfigurationView must contain config-day-minutes ID", viewContent.contains("id=\"config-day-minutes\""));
		assertTrue("ConfigurationView must contain config-vacation-code ID", viewContent.contains("id=\"config-vacation-code\""));
		assertTrue("ConfigurationView must contain config-version-badge ID", viewContent.contains("id=\"config-version-badge\""));
		assertTrue("ConfigurationView must contain config-updated-by-badge ID", viewContent.contains("id=\"config-updated-by-badge\""));
		assertTrue("ConfigurationView must contain save-config-btn ID", viewContent.contains("id=\"save-config-btn\""));
		assertTrue("ConfigurationView must contain reload-config-btn ID", viewContent.contains("id=\"reload-config-btn\""));

		// 3. Verify index.html navigation and header branding
		File indexFile = new File(webappDir, "index.html");
		assertTrue("index.html must exist", indexFile.exists());
		String indexContent = Files.readString(indexFile.toPath());
		assertTrue("index.html must contain configuration link for Administrator",
				indexContent.contains("data-roles=\"Administrator\"") && indexContent.contains("href=\"#configuration\""));
		assertTrue("index.html must contain header-branding", indexContent.contains("id=\"header-branding\""));
		assertTrue("index.html must contain header-logo", indexContent.contains("id=\"header-logo\""));
		assertTrue("index.html must contain chronivaro-logo-light.svg", indexContent.contains("src=\"assets/icons/chronivaro-logo-light.svg\""));

		// 4. Verify app.js routing and branding management
		File appFile = new File(webappDir, "js/app.js");
		assertTrue("app.js must exist", appFile.exists());
		String appContent = Files.readString(appFile.toPath());
		assertTrue("app.js must import ConfigurationView", appContent.contains("import ConfigurationView from './pages/ConfigurationView.js';"));
		assertTrue("app.js must route configuration", appContent.contains("case 'configuration':"));
		assertTrue("app.js must instantiate ConfigurationView", appContent.contains("view = new ConfigurationView(this);"));
		assertTrue("app.js must define loadBranding", appContent.contains("loadBranding"));
		assertTrue("app.js must define updateBranding", appContent.contains("updateBranding"));

		// 5. Verify style.css styling
		File cssFile = new File(webappDir, "assets/css/style.css");
		assertTrue("style.css must exist", cssFile.exists());
		String cssContent = Files.readString(cssFile.toPath());
		assertTrue("style.css must define #configuration-view", cssContent.contains("#configuration-view"));
		assertTrue("style.css must define .configuration-container", cssContent.contains(".configuration-container"));
		assertTrue("style.css must define .config-card", cssContent.contains(".config-card"));
		assertTrue("style.css must define .header-branding", cssContent.contains(".header-branding"));
		assertTrue("style.css must define .header-logo", cssContent.contains(".header-logo"));
	}

	@Test
	public void shouldExecuteConfigurationLifecycleFlow() {
		String adminToken = authenticate("admin", "admin");

		// Step 1: Read initial configuration
		ConfigurationDto initialDto;
		String initialEtag;
		try (Response response = target()
				.path("chronivaro/v1/admin/configuration")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			initialEtag = response.getHeaderString("ETag");
			assertNotNull("ETag must be present", initialEtag);

			initialDto = gson.fromJson(response.readEntity(String.class), ConfigurationDto.class);
			assertNotNull("Configuration DTO must not be null", initialDto);
			assertEquals(Integer.valueOf(2520), initialDto.weeklyTargetMinutes());
			assertEquals(Integer.valueOf(25), initialDto.annualVacationDays());
			assertEquals(Integer.valueOf(480), initialDto.minutesPerVacationDay());
			assertEquals("VACATION", initialDto.vacationAbsenceTypeCode());
			assertEquals("Chronivaro", initialDto.companyName());
			assertEquals("de", initialDto.defaultLanguage());
		}

		// Step 2: Update configuration with valid parameters
		String updateJson = """
				{
				  "weeklyTargetMinutes": 2400,
				  "annualVacationDays": 30,
				  "minutesPerVacationDay": 480,
				  "vacationAbsenceTypeCode": "VACATION",
				  "companyName": "Acme Time Corp",
				  "companyLogo": "https://example.com/logo.png",
				  "defaultLanguage": "en"
				}
				""";

		ConfigurationDto updatedDto;
		String updatedEtag;
		try (Response response = target()
				.path("chronivaro/v1/admin/configuration")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.header("If-Match", initialEtag)
				.put(Entity.json(updateJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			updatedEtag = response.getHeaderString("ETag");
			assertNotNull("Updated ETag must be present", updatedEtag);
			assertNotEquals("ETag must change after update", initialEtag, updatedEtag);

			updatedDto = gson.fromJson(response.readEntity(String.class), ConfigurationDto.class);
			assertEquals(Integer.valueOf(2400), updatedDto.weeklyTargetMinutes());
			assertEquals(Integer.valueOf(30), updatedDto.annualVacationDays());
			assertEquals(Integer.valueOf(480), updatedDto.minutesPerVacationDay());
			assertEquals("VACATION", updatedDto.vacationAbsenceTypeCode());
			assertEquals("Acme Time Corp", updatedDto.companyName());
			assertEquals("https://example.com/logo.png", updatedDto.companyLogo());
			assertEquals("en", updatedDto.defaultLanguage());
			assertEquals("admin", updatedDto.updatedBy());
			assertEquals(Integer.valueOf(initialDto.version() + 1), updatedDto.version());
		}

		// Step 3: Verify subsequent GET returns the updated values
		try (Response response = target()
				.path("chronivaro/v1/admin/configuration")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			ConfigurationDto fetched = gson.fromJson(response.readEntity(String.class), ConfigurationDto.class);
			assertEquals(Integer.valueOf(2400), fetched.weeklyTargetMinutes());
			assertEquals(Integer.valueOf(30), fetched.annualVacationDays());
			assertEquals(Integer.valueOf(480), fetched.minutesPerVacationDay());
			assertEquals("Acme Time Corp", fetched.companyName());
			assertEquals("https://example.com/logo.png", fetched.companyLogo());
			assertEquals("en", fetched.defaultLanguage());
		}

		// Step 4: Stale optimistic concurrency conflict test (using old initialEtag)
		try (Response response = target()
				.path("chronivaro/v1/admin/configuration")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.header("If-Match", initialEtag)
				.put(Entity.json(updateJson))) {
			assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
		}

		// Step 5: Boundary validation test (invalid weeklyTargetMinutes)
		String invalidWeeklyJson = """
				{
				  "weeklyTargetMinutes": -10,
				  "annualVacationDays": 25,
				  "minutesPerVacationDay": 480,
				  "vacationAbsenceTypeCode": "VACATION"
				}
				""";
		try (Response response = target()
				.path("chronivaro/v1/admin/configuration")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.header("If-Match", updatedEtag)
				.put(Entity.json(invalidWeeklyJson))) {
			assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
		}
	}
}
