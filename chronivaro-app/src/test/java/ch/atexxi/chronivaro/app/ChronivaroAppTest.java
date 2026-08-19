package ch.atexxi.chronivaro.app;

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
}
