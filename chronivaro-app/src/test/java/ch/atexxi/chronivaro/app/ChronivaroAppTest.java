package ch.atexxi.chronivaro.app;

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
	public void shouldPreserveArchitecturalSeparationWithoutJettyInCoreOrRest() {
		// Verify that chronivaro-core and chronivaro-rest classes have no dependencies on org.eclipse.jetty
		Class<?>[] nonJettyClasses = new Class<?>[]{
				ch.atexxi.chronivaro.core.model.ChronivaroModelHelper.class,
				ch.atexxi.chronivaro.core.service.PresenceService.class,
				ch.atexxi.chronivaro.rest.resource.ChronivaroResource.class,
				ch.atexxi.chronivaro.rest.resource.AbsenceResource.class,
				ch.atexxi.chronivaro.rest.resource.ConfigurationResource.class,
				ch.atexxi.chronivaro.rest.resource.ReportsResource.class,
				ch.atexxi.chronivaro.rest.ChronivaroRestfulClasses.class
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
