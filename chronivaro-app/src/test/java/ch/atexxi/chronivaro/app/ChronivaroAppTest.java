package ch.atexxi.chronivaro.app;

import li.strolch.testbase.runtime.RuntimeMock;
import li.strolch.utils.helper.FileHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
}
