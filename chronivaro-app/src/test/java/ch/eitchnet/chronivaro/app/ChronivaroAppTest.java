package ch.eitchnet.chronivaro.app;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import li.strolch.testbase.runtime.RuntimeMock;
import li.strolch.utils.helper.FileHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Base64;

import static org.junit.Assert.*;

public class ChronivaroAppTest {

	private static final String TARGET_PATH = "target/ChronivaroAppTest";
	private static final String SOURCE_PATH = "src/test/resources/runtime";

	private ChronivaroApp app;

	@Before
	public void setUp() {
		File targetDir = new File(TARGET_PATH);
		if (targetDir.exists()) {
			FileHelper.deleteFile(targetDir, true);
		}
		new RuntimeMock().mockRuntime(TARGET_PATH, SOURCE_PATH);
	}

	@After
	public void tearDown() {
		if (this.app != null) {
			this.app.stop();
			this.app = null;
		}
		File targetDir = new File(TARGET_PATH);
		if (targetDir.exists()) {
			FileHelper.deleteFile(targetDir, true);
		}
	}

	@Test
	public void shouldStartAndStopWithoutHttp() throws Exception {
		ChronivaroAppConfig config = new ChronivaroAppConfig(
				false,
				"127.0.0.1",
				0,
				"/",
				null,
				TARGET_PATH,
				"dev"
		);

		this.app = new ChronivaroApp(config);
		assertFalse(this.app.isRunning());

		this.app.start();
		assertTrue(this.app.isRunning());
		assertNotNull(this.app.getAgent());
		assertTrue(this.app.getAgent().getContainer().hasComponent(li.strolch.runtime.privilege.PrivilegeHandler.class));

		this.app.stop();
		assertFalse(this.app.isRunning());
	}

	@Test
	public void shouldStartEmbeddedHttpServerOnEphemeralPort() throws Exception {
		ChronivaroAppConfig config = new ChronivaroAppConfig(
				true,
				"127.0.0.1",
				0,
				"/",
				null,
				TARGET_PATH,
				"dev"
		);

		this.app = new ChronivaroApp(config);
		this.app.start();

		assertTrue(this.app.isRunning());
		int boundPort = this.app.getPort();
		assertTrue("Bound port should be > 0, but was " + boundPort, boundPort > 0);

		// Perform HTTP GET request against the embedded server
		HttpClient httpClient = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/strolch/version"))
				.GET()
				.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		assertTrue("Expected 200 OK or 401 Unauthorized depending on auth, but got " + response.statusCode(),
				response.statusCode() == 200 || response.statusCode() == 401 || response.statusCode() == 404);

		this.app.stop();
		assertFalse(this.app.isRunning());
	}

	@Test
	public void shouldFailStartupAndCleanUpOnPortBindConflict() throws Exception {
		try (ServerSocket blockingSocket = new ServerSocket(0)) {
			int conflictPort = blockingSocket.getLocalPort();
			ChronivaroAppConfig config = new ChronivaroAppConfig(
					true,
					"127.0.0.1",
					conflictPort,
					"/",
					null,
					TARGET_PATH,
					"dev"
			);

			this.app = new ChronivaroApp(config);
			try {
				this.app.start();
				fail("Expected startup to fail due to port conflict on port " + conflictPort);
			} catch (Throwable expected) {
				assertFalse(this.app.isRunning());
				assertNull(this.app.getAgent());
			}
		}
	}

	@Test
	public void shouldSupportCustomContextPath() throws Exception {
		ChronivaroAppConfig config = new ChronivaroAppConfig(
				true,
				"127.0.0.1",
				0,
				"/chronivaro",
				null,
				TARGET_PATH,
				"dev"
		);

		this.app = new ChronivaroApp(config);
		this.app.start();

		assertTrue(this.app.isRunning());
		int boundPort = this.app.getPort();

		HttpClient httpClient = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/chronivaro/rest/strolch/version"))
				.GET()
				.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		assertTrue("Expected 200/401/404 on context path, got " + response.statusCode(),
				response.statusCode() == 200 || response.statusCode() == 401 || response.statusCode() == 404);

		HttpRequest badContextRequest = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/strolch/version"))
				.GET()
				.build();
		HttpResponse<String> badResponse = httpClient.send(badContextRequest, HttpResponse.BodyHandlers.ofString());
		assertEquals(404, badResponse.statusCode());

		this.app.stop();
		assertFalse(this.app.isRunning());
	}

	@Test
	public void shouldSupportCustomWebResourcePath() throws Exception {
		File customWebDir = new File(TARGET_PATH, "custom-web");
		customWebDir.mkdirs();
		File testFile = new File(customWebDir, "custom.txt");
		Files.writeString(testFile.toPath(), "custom-content");

		ChronivaroAppConfig config = new ChronivaroAppConfig(
				true,
				"127.0.0.1",
				0,
				"/",
				customWebDir.getAbsolutePath(),
				TARGET_PATH,
				"dev"
		);

		this.app = new ChronivaroApp(config);
		this.app.start();

		assertTrue(this.app.isRunning());
		int boundPort = this.app.getPort();

		HttpClient httpClient = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/custom.txt"))
				.GET()
				.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, response.statusCode());
		assertEquals("custom-content", response.body());

		this.app.stop();
		assertFalse(this.app.isRunning());
	}

	@Test
	public void shouldHandleIdempotentStopAndShutdownHook() throws Exception {
		ChronivaroAppConfig config = new ChronivaroAppConfig(
				false,
				"127.0.0.1",
				0,
				"/",
				null,
				TARGET_PATH,
				"dev"
		);

		this.app = new ChronivaroApp(config);
		Thread hook = this.app.registerShutdownHook();
		assertNotNull(hook);

		// Stopping an unstarted app is a safe no-op
		this.app.stop();
		assertFalse(this.app.isRunning());

		this.app.start();
		assertTrue(this.app.isRunning());

		// Multiple stop calls are idempotent
		this.app.stop();
		assertFalse(this.app.isRunning());
		this.app.stop();
		assertFalse(this.app.isRunning());
	}

	@Test
	public void shouldServeFrontendIndexAndStaticAssetsFromSameServer() throws Exception {
		ChronivaroAppConfig config = new ChronivaroAppConfig(
				true,
				"127.0.0.1",
				0,
				"/",
				null,
				TARGET_PATH,
				"dev"
		);

		this.app = new ChronivaroApp(config);
		this.app.start();

		assertTrue(this.app.isRunning());
		int boundPort = this.app.getPort();
		HttpClient httpClient = HttpClient.newHttpClient();

		// 1. Root welcome file (index.html)
		HttpRequest rootReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/"))
				.GET()
				.build();
		HttpResponse<String> rootRes = httpClient.send(rootReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, rootRes.statusCode());
		assertTrue(rootRes.body().contains("<title>Chronivaro</title>"));
		assertTrue(rootRes.body().contains("<main id=\"app\">"));

		// 2. Direct index.html
		HttpRequest indexReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/index.html"))
				.GET()
				.build();
		HttpResponse<String> indexRes = httpClient.send(indexReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, indexRes.statusCode());
		assertTrue(indexRes.body().contains("<script type=\"module\" src=\"js/app.js\"></script>"));

		// 3. CSS Stylesheet (/assets/css/style.css)
		HttpRequest cssReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/assets/css/style.css"))
				.GET()
				.build();
		HttpResponse<String> cssRes = httpClient.send(cssReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, cssRes.statusCode());
		assertTrue(cssRes.body().contains("body") || cssRes.body().contains(".container"));

		// 4. JS Application bundle (/js/app.js)
		HttpRequest jsReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/js/app.js"))
				.GET()
				.build();
		HttpResponse<String> jsRes = httpClient.send(jsReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, jsRes.statusCode());
		assertTrue(jsRes.body().contains("App"));

		// 5. Non-existent static resource should return 404
		HttpRequest missingReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/nonexistent-file.html"))
				.GET()
				.build();
		HttpResponse<String> missingRes = httpClient.send(missingReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(404, missingRes.statusCode());

		this.app.stop();
		assertFalse(this.app.isRunning());
	}

	@Test
	public void shouldServeRestEndpointsAndAuthenticateThroughJersey() throws Exception {
		ChronivaroAppConfig config = new ChronivaroAppConfig(
				true,
				"127.0.0.1",
				0,
				"/",
				null,
				TARGET_PATH,
				"dev"
		);

		this.app = new ChronivaroApp(config);
		this.app.start();

		assertTrue(this.app.isRunning());
		int boundPort = this.app.getPort();
		HttpClient httpClient = HttpClient.newHttpClient();

		// 1. Unauthenticated request to protected endpoint should yield 401
		HttpRequest unauthReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/admin/configuration"))
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<String> unauthRes = httpClient.send(unauthReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(401, unauthRes.statusCode());

		// 2. Authenticate via /rest/strolch/authentication
		JsonObject authPayload = new JsonObject();
		authPayload.addProperty("username", "admin");
		authPayload.addProperty("password", Base64.getEncoder().encodeToString("admin".getBytes()));

		HttpRequest authReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/strolch/authentication"))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(authPayload.toString()))
				.build();
		HttpResponse<String> authRes = httpClient.send(authReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, authRes.statusCode());

		JsonObject authJson = JsonParser.parseString(authRes.body()).getAsJsonObject();
		assertTrue(authJson.has("authToken"));
		String authToken = authJson.get("authToken").getAsString();
		assertNotNull(authToken);
		assertFalse(authToken.isEmpty());

		// 3. Authenticated query to /rest/chronivaro/v1/admin/configuration
		HttpRequest configReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/admin/configuration"))
				.header("Authorization", authToken)
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<String> configRes = httpClient.send(configReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, configRes.statusCode());
		JsonObject configDto = JsonParser.parseString(configRes.body()).getAsJsonObject();
		assertTrue(configDto.has("weeklyTargetMinutes"));
		assertTrue(configDto.has("annualVacationDays"));

		// 4. Authenticated query to /rest/chronivaro/v1/admin/employees
		HttpRequest empReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/admin/employees"))
				.header("Authorization", authToken)
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<String> empRes = httpClient.send(empReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, empRes.statusCode());

		// 5. Authenticated query to /rest/chronivaro/v1/presence
		HttpRequest presReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/presence"))
				.header("Authorization", authToken)
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<String> presRes = httpClient.send(presReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, presRes.statusCode());

		// 6. Authenticated query to /rest/chronivaro/v1/reports/day
		HttpRequest reportReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/reports/day?date=2026-08-19"))
				.header("Authorization", authToken)
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<String> reportRes = httpClient.send(reportReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, reportRes.statusCode());

		// 7. Non-existent REST endpoint should return 404
		HttpRequest badRestReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/nonexistent/endpoint"))
				.header("Authorization", authToken)
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<String> badRestRes = httpClient.send(badRestReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(404, badRestRes.statusCode());

		this.app.stop();
		assertFalse(this.app.isRunning());
	}

	@Test
	public void shouldServeSystemHealthReadinessVersionAndMetricsEndpointsWithoutAuth() throws Exception {
		ChronivaroAppConfig config = new ChronivaroAppConfig(
				true,
				"127.0.0.1",
				0,
				"/",
				null,
				TARGET_PATH,
				"dev"
		);

		this.app = new ChronivaroApp(config);
		this.app.start();

		assertTrue(this.app.isRunning());
		int boundPort = this.app.getPort();
		HttpClient httpClient = HttpClient.newHttpClient();

		// 1. Health probe (/rest/chronivaro/v1/system/health)
		HttpRequest healthReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/system/health"))
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<String> healthRes = httpClient.send(healthReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, healthRes.statusCode());
		JsonObject healthJson = JsonParser.parseString(healthRes.body()).getAsJsonObject();
		assertEquals("UP", healthJson.get("status").getAsString());
		assertTrue("agentState should be STARTED or RUNNING, got: " + healthJson.get("agentState").getAsString(),
				"STARTED".equals(healthJson.get("agentState").getAsString()) || "RUNNING".equals(healthJson.get("agentState").getAsString()));
		assertTrue(healthJson.get("uptimeMs").getAsLong() >= 0);
		assertTrue(healthJson.has("timestamp"));

		// 2. Readiness probe (/rest/chronivaro/v1/system/readiness)
		HttpRequest readinessReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/system/readiness"))
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<String> readinessRes = httpClient.send(readinessReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, readinessRes.statusCode());
		JsonObject readinessJson = JsonParser.parseString(readinessRes.body()).getAsJsonObject();
		assertEquals("READY", readinessJson.get("status").getAsString());
		assertTrue("agentState should be STARTED or RUNNING, got: " + readinessJson.get("agentState").getAsString(),
				"STARTED".equals(readinessJson.get("agentState").getAsString()) || "RUNNING".equals(readinessJson.get("agentState").getAsString()));
		assertTrue(readinessJson.has("activeRealms"));
		assertTrue(readinessJson.get("activeRealms").getAsJsonArray().size() > 0);

		// 3. Version probe (/rest/chronivaro/v1/system/version)
		HttpRequest versionReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/system/version"))
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<String> versionRes = httpClient.send(versionReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, versionRes.statusCode());
		JsonObject versionJson = JsonParser.parseString(versionRes.body()).getAsJsonObject();
		assertTrue(versionJson.has("version"));
		assertTrue(versionJson.has("buildTimestamp"));
		assertTrue(versionJson.has("environment"));

		// 4. Root version alias (/rest/chronivaro/v1/version)
		HttpRequest versionAliasReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/version"))
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<String> versionAliasRes = httpClient.send(versionAliasReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, versionAliasRes.statusCode());

		// 5. System metrics probe (/rest/chronivaro/v1/system/metrics)
		HttpRequest metricsReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/system/metrics"))
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<String> metricsRes = httpClient.send(metricsReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, metricsRes.statusCode());
		JsonObject metricsJson = JsonParser.parseString(metricsRes.body()).getAsJsonObject();
		assertTrue(metricsJson.get("heapUsedBytes").getAsLong() > 0);
		assertTrue(metricsJson.get("heapMaxBytes").getAsLong() > 0);
		assertTrue(metricsJson.get("activeThreads").getAsInt() > 0);
		assertTrue(metricsJson.get("availableProcessors").getAsInt() > 0);
		assertTrue(metricsJson.has("uptimeMs"));

		this.app.stop();
		assertFalse(this.app.isRunning());
	}

	@Test
	public void shouldPropagateCorrelationIdAndStructuredErrorResponses() throws Exception {
		ChronivaroAppConfig config = new ChronivaroAppConfig(
				true,
				"127.0.0.1",
				0,
				"/",
				null,
				TARGET_PATH,
				"dev"
		);

		this.app = new ChronivaroApp(config);
		this.app.start();

		int boundPort = this.app.getPort();
		HttpClient httpClient = HttpClient.newHttpClient();

		// 1. Explicit correlation ID propagation on successful request
		String customCorrId = "corr-test-custom-12345";
		HttpRequest customCorrReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/system/health"))
				.header("X-Correlation-Id", customCorrId)
				.GET()
				.build();
		HttpResponse<String> customCorrRes = httpClient.send(customCorrReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, customCorrRes.statusCode());
		assertTrue(customCorrRes.headers().firstValue("X-Correlation-Id").isPresent());
		assertEquals(customCorrId, customCorrRes.headers().firstValue("X-Correlation-Id").get());

		// 2. Generated correlation ID when none provided
		HttpRequest autoCorrReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/system/health"))
				.GET()
				.build();
		HttpResponse<String> autoCorrRes = httpClient.send(autoCorrReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, autoCorrRes.statusCode());
		assertTrue(autoCorrRes.headers().firstValue("X-Correlation-Id").isPresent());
		String generatedCorrId = autoCorrRes.headers().firstValue("X-Correlation-Id").get();
		assertNotNull(generatedCorrId);
		assertFalse(generatedCorrId.isEmpty());

		// 3. Correlation ID preservation on unauthenticated error
		String errCorrId = "corr-err-unauth-456";
		HttpRequest unauthReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/admin/configuration"))
				.header("X-Correlation-Id", errCorrId)
				.GET()
				.build();
		HttpResponse<String> unauthRes = httpClient.send(unauthReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(401, unauthRes.statusCode());
		assertTrue(unauthRes.headers().firstValue("X-Correlation-Id").isPresent());
		assertEquals(errCorrId, unauthRes.headers().firstValue("X-Correlation-Id").get());

		this.app.stop();
		assertFalse(this.app.isRunning());
	}

	@Test
	public void shouldMeetPerformanceAndPaginationRequirements() throws Exception {
		ChronivaroAppConfig config = new ChronivaroAppConfig(
				true,
				"127.0.0.1",
				0,
				"/",
				null,
				TARGET_PATH,
				"dev"
		);

		this.app = new ChronivaroApp(config);
		this.app.start();

		int boundPort = this.app.getPort();
		HttpClient httpClient = HttpClient.newHttpClient();

		// Authenticate
		JsonObject authPayload = new JsonObject();
		authPayload.addProperty("username", "admin");
		authPayload.addProperty("password", Base64.getEncoder().encodeToString("admin".getBytes()));

		HttpRequest authReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/strolch/authentication"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(authPayload.toString()))
				.build();
		HttpResponse<String> authRes = httpClient.send(authReq, HttpResponse.BodyHandlers.ofString());
		String authToken = JsonParser.parseString(authRes.body()).getAsJsonObject().get("authToken").getAsString();

		// 1. Day report SLA (< 2000ms)
		long startDay = System.currentTimeMillis();
		HttpRequest dayReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/reports/day?date=2026-08-19"))
				.header("Authorization", authToken)
				.GET()
				.build();
		HttpResponse<String> dayRes = httpClient.send(dayReq, HttpResponse.BodyHandlers.ofString());
		long durationDay = System.currentTimeMillis() - startDay;
		assertEquals(200, dayRes.statusCode());
		assertTrue("Day report response took " + durationDay + "ms (SLA: <2000ms)", durationDay < 2000);

		// 2. Month report SLA (< 2000ms)
		long startMonth = System.currentTimeMillis();
		HttpRequest monthReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/reports/month?yearMonth=2026-08"))
				.header("Authorization", authToken)
				.GET()
				.build();
		HttpResponse<String> monthRes = httpClient.send(monthReq, HttpResponse.BodyHandlers.ofString());
		long durationMonth = System.currentTimeMillis() - startMonth;
		assertEquals(200, monthRes.statusCode());
		assertTrue("Month report response took " + durationMonth + "ms (SLA: <2000ms)", durationMonth < 2000);

		// 3. Team report SLA (< 5000ms)
		long startTeam = System.currentTimeMillis();
		HttpRequest teamReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/reports/team?teamId=team-1&yearMonth=2026-08"))
				.header("Authorization", authToken)
				.GET()
				.build();
		HttpResponse<String> teamRes = httpClient.send(teamReq, HttpResponse.BodyHandlers.ofString());
		long durationTeam = System.currentTimeMillis() - startTeam;
		assertEquals(200, teamRes.statusCode());
		assertTrue("Team report response took " + durationTeam + "ms (SLA: <5000ms)", durationTeam < 5000);

		// 4. Server-side pagination query
		HttpRequest pagedReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/presence?offset=0&limit=2"))
				.header("Authorization", authToken)
				.GET()
				.build();
		HttpResponse<String> pagedRes = httpClient.send(pagedReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, pagedRes.statusCode());
		JsonObject pagedJson = JsonParser.parseString(pagedRes.body()).getAsJsonObject();
		assertTrue(pagedJson.has("data"));
		assertTrue(pagedJson.has("offset"));
		assertTrue(pagedJson.has("limit"));
		assertTrue(pagedJson.has("total"));
		assertEquals(0, pagedJson.get("offset").getAsInt());
		assertEquals(2, pagedJson.get("limit").getAsInt());
		assertTrue(pagedJson.get("data").getAsJsonArray().size() <= 2);

		this.app.stop();
		assertFalse(this.app.isRunning());
	}

	@Test
	public void shouldEnforceRoleBasedDataPrivacyAcrossRoles() throws Exception {
		ChronivaroAppConfig config = new ChronivaroAppConfig(
				true,
				"127.0.0.1",
				0,
				"/",
				null,
				TARGET_PATH,
				"dev"
		);

		this.app = new ChronivaroApp(config);
		this.app.start();

		int boundPort = this.app.getPort();
		HttpClient httpClient = HttpClient.newHttpClient();

		// Employee login
		JsonObject empAuthPayload = new JsonObject();
		empAuthPayload.addProperty("username", "employee");
		empAuthPayload.addProperty("password", Base64.getEncoder().encodeToString("admin".getBytes()));

		HttpRequest empAuthReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/strolch/authentication"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(empAuthPayload.toString()))
				.build();
		HttpResponse<String> empAuthRes = httpClient.send(empAuthReq, HttpResponse.BodyHandlers.ofString());
		String empToken = JsonParser.parseString(empAuthRes.body()).getAsJsonObject().get("authToken").getAsString();

		// Employee attempting to access administrative audit logs should be forbidden (403 or 401)
		HttpRequest auditReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/admin/audit-logs"))
				.header("Authorization", empToken)
				.GET()
				.build();
		HttpResponse<String> auditRes = httpClient.send(auditReq, HttpResponse.BodyHandlers.ofString());
		assertTrue("Expected 401 or 403 for unauthorized audit log access, got: " + auditRes.statusCode(),
				auditRes.statusCode() == 401 || auditRes.statusCode() == 403);

		// Employee attempting to update configuration should be forbidden
		JsonObject configPayload = new JsonObject();
		configPayload.addProperty("weeklyTargetMinutes", 2400);
		configPayload.addProperty("annualVacationDays", 25);
		configPayload.addProperty("minutesPerVacationDay", 480);
		configPayload.addProperty("vacationAbsenceCode", "VACATION");

		HttpRequest updateConfigReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/chronivaro/v1/admin/configuration"))
				.header("Authorization", empToken)
				.header("Content-Type", "application/json")
				.header("If-Match", "\"0\"")
				.PUT(HttpRequest.BodyPublishers.ofString(configPayload.toString()))
				.build();
		HttpResponse<String> updateConfigRes = httpClient.send(updateConfigReq, HttpResponse.BodyHandlers.ofString());
		assertTrue("Expected 401 or 403 for unauthorized config update, got: " + updateConfigRes.statusCode(),
				updateConfigRes.statusCode() == 401 || updateConfigRes.statusCode() == 403);

		this.app.stop();
		assertFalse(this.app.isRunning());
	}

	@Test
	public void shouldComplyWithWebUiAccessibilityAndResponsiveStandards() throws Exception {
		ChronivaroAppConfig config = new ChronivaroAppConfig(
				true,
				"127.0.0.1",
				0,
				"/",
				null,
				TARGET_PATH,
				"dev"
		);

		this.app = new ChronivaroApp(config);
		this.app.start();

		int boundPort = this.app.getPort();
		HttpClient httpClient = HttpClient.newHttpClient();

		// Fetch index.html
		HttpRequest indexReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/index.html"))
				.GET()
				.build();
		HttpResponse<String> indexRes = httpClient.send(indexReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, indexRes.statusCode());
		String html = indexRes.body();

		// 1. Viewport meta tag for mobile responsiveness
		assertTrue("index.html must include mobile viewport meta tag",
				html.contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"));

		// 2. HTML language attribute for screen readers
		assertTrue("index.html must declare lang attribute", html.contains("<html lang=\"en\">"));

		// 3. Navigation and Main landmarks
		assertTrue("index.html must include semantic navigation landmark", html.contains("<nav"));
		assertTrue("index.html must include semantic main landmark", html.contains("<main id=\"app\""));

		// 4. Accessible branding and header
		assertTrue(html.contains("<header") || html.contains("header"));

		this.app.stop();
		assertFalse(this.app.isRunning());
	}

	@Test
	public void shouldAllowUserToChangeOwnPasswordAndAuthenticateWithNewPassword() throws Exception {
		ChronivaroAppConfig config = new ChronivaroAppConfig(
				true,
				"127.0.0.1",
				0,
				"/",
				null,
				TARGET_PATH,
				"dev"
		);

		this.app = new ChronivaroApp(config);
		this.app.start();

		int boundPort = this.app.getPort();
		HttpClient httpClient = HttpClient.newHttpClient();

		// 1. Employee login with initial password
		JsonObject empAuthPayload = new JsonObject();
		empAuthPayload.addProperty("username", "employee");
		empAuthPayload.addProperty("password", Base64.getEncoder().encodeToString("admin".getBytes()));

		HttpRequest empAuthReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/strolch/authentication"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(empAuthPayload.toString()))
				.build();
		HttpResponse<String> empAuthRes = httpClient.send(empAuthReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, empAuthRes.statusCode());
		JsonObject empAuthJson = JsonParser.parseString(empAuthRes.body()).getAsJsonObject();
		String empToken = empAuthJson.get("authToken").getAsString();
		String empUserId = empAuthJson.has("userId") ? empAuthJson.get("userId").getAsString() : "employee";
		assertNotNull(empToken);

		// 2. Change password for employee using userId from auth response
		JsonObject changePwdPayload = new JsonObject();
		changePwdPayload.addProperty("password", Base64.getEncoder().encodeToString("NewPassword456!".getBytes()));

		HttpRequest changePwdReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/strolch/privilege/users/" + empUserId + "/password"))
				.header("Authorization", empToken)
				.header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(changePwdPayload.toString()))
				.build();
		HttpResponse<String> changePwdRes = httpClient.send(changePwdReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, changePwdRes.statusCode());

		// 3. Login with old password should fail
		HttpRequest oldLoginReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/strolch/authentication"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(empAuthPayload.toString()))
				.build();
		HttpResponse<String> oldLoginRes = httpClient.send(oldLoginReq, HttpResponse.BodyHandlers.ofString());
		assertTrue("Login with old password should fail, got status: " + oldLoginRes.statusCode(),
				oldLoginRes.statusCode() == 401 || oldLoginRes.statusCode() == 403 || oldLoginRes.statusCode() == 400);

		// 4. Login with new password should succeed
		JsonObject newAuthPayload = new JsonObject();
		newAuthPayload.addProperty("username", "employee");
		newAuthPayload.addProperty("password", Base64.getEncoder().encodeToString("NewPassword456!".getBytes()));

		HttpRequest newLoginReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/strolch/authentication"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(newAuthPayload.toString()))
				.build();
		HttpResponse<String> newLoginRes = httpClient.send(newLoginReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, newLoginRes.statusCode());
		JsonObject newLoginJson = JsonParser.parseString(newLoginRes.body()).getAsJsonObject();
		assertTrue(newLoginJson.has("authToken"));
		assertEquals("employee", newLoginJson.get("username").getAsString());

		// 5. Admin user password change using userId from auth response
		JsonObject adminAuthPayload = new JsonObject();
		adminAuthPayload.addProperty("username", "admin");
		adminAuthPayload.addProperty("password", Base64.getEncoder().encodeToString("admin".getBytes()));

		HttpRequest adminAuthReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/strolch/authentication"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(adminAuthPayload.toString()))
				.build();
		HttpResponse<String> adminAuthRes = httpClient.send(adminAuthReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, adminAuthRes.statusCode());
		JsonObject adminAuthJson = JsonParser.parseString(adminAuthRes.body()).getAsJsonObject();
		String adminToken = adminAuthJson.get("authToken").getAsString();
		String adminUserId = adminAuthJson.get("userId").getAsString();

		JsonObject adminChangePwdPayload = new JsonObject();
		adminChangePwdPayload.addProperty("password", Base64.getEncoder().encodeToString("AdminNewPassword789!".getBytes()));

		HttpRequest adminChangePwdReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/strolch/privilege/users/" + adminUserId + "/password"))
				.header("Authorization", adminToken)
				.header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(adminChangePwdPayload.toString()))
				.build();
		HttpResponse<String> adminChangePwdRes = httpClient.send(adminChangePwdReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, adminChangePwdRes.statusCode());

		JsonObject adminReAuthPayload = new JsonObject();
		adminReAuthPayload.addProperty("username", "admin");
		adminReAuthPayload.addProperty("password", Base64.getEncoder().encodeToString("AdminNewPassword789!".getBytes()));

		HttpRequest adminReAuthReq = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + boundPort + "/rest/strolch/authentication"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(adminReAuthPayload.toString()))
				.build();
		HttpResponse<String> adminReAuthRes = httpClient.send(adminReAuthReq, HttpResponse.BodyHandlers.ofString());
		assertEquals(200, adminReAuthRes.statusCode());

		this.app.stop();
		assertFalse(this.app.isRunning());
	}

	@Test
	public void shouldPreserveArchitecturalSeparationWithoutJettyInCoreOrRest() {
		// Verify that chronivaro-core and chronivaro-rest classes have no dependencies on org.eclipse.jetty
		Class<?>[] nonJettyClasses = new Class<?>[]{
				ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper.class,
				ch.eitchnet.chronivaro.core.service.PresenceService.class,
				ch.eitchnet.chronivaro.rest.resource.ChronivaroResource.class,
				ch.eitchnet.chronivaro.rest.resource.AbsenceResource.class,
				ch.eitchnet.chronivaro.rest.resource.ConfigurationResource.class,
				ch.eitchnet.chronivaro.rest.resource.ReportsResource.class,
				ch.eitchnet.chronivaro.rest.ChronivaroRestfulClasses.class
		};

		for (Class<?> clazz : nonJettyClasses) {
			for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
				for (Class<?> paramType : method.getParameterTypes()) {
					assertFalse("Class " + clazz.getName() + " leaks Jetty dependency in parameter " + paramType.getName(),
							paramType.getName().startsWith("org.eclipse.jetty"));
				}
				assertFalse("Class " + clazz.getName() + " leaks Jetty dependency in return type " + method.getReturnType().getName(),
						method.getReturnType().getName().startsWith("org.eclipse.jetty"));
			}
			for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
				assertFalse("Class " + clazz.getName() + " leaks Jetty dependency in field " + field.getName(),
						field.getType().getName().startsWith("org.eclipse.jetty"));
			}
		}
	}
}
