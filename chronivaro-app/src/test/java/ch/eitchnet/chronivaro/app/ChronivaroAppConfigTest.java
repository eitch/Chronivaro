package ch.eitchnet.chronivaro.app;

import org.junit.Test;

import static org.junit.Assert.*;

public class ChronivaroAppConfigTest {

	@Test
	public void shouldCreateDefaultConfig() {
		ChronivaroAppConfig config = ChronivaroAppConfig.defaultConfig();
		assertTrue(config.httpEnabled());
		assertEquals("0.0.0.0", config.bindAddress());
		assertEquals(9000, config.port());
		assertEquals("/", config.contextPath());
		assertEquals("dev", config.strolchEnvironment());
		assertNotNull(config.strolchPath());
	}

	@Test
	public void shouldParseCommandLineArguments() {
		String[] args = {
				"--port", "9090",
				"--bind", "127.0.0.1",
				"--context-path", "/test",
				"--runtime", "/var/chronivaro/runtime",
				"--env", "prod",
				"--web-resources", "/var/chronivaro/web"
		};

		ChronivaroAppConfig config = ChronivaroAppConfig.fromArgsAndEnv(args);
		assertTrue(config.httpEnabled());
		assertEquals("127.0.0.1", config.bindAddress());
		assertEquals(9090, config.port());
		assertEquals("/test", config.contextPath());
		assertEquals("/var/chronivaro/runtime", config.strolchPath());
		assertEquals("prod", config.strolchEnvironment());
		assertEquals("/var/chronivaro/web", config.webResourcePath());
	}

	@Test
	public void shouldSupportDisablingHttp() {
		String[] args = {"--no-http"};
		ChronivaroAppConfig config = ChronivaroAppConfig.fromArgsAndEnv(args);
		assertFalse(config.httpEnabled());
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldRejectInvalidPort() {
		new ChronivaroAppConfig(true, "0.0.0.0", 70000, "/", null, "runtime", "dev");
	}

	@Test
	public void shouldFormatContextPath() {
		ChronivaroAppConfig config = new ChronivaroAppConfig(true, "0.0.0.0", 8080, "my-app", null, "runtime", "dev");
		assertEquals("/my-app", config.contextPath());
	}
}
