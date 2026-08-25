package ch.eitchnet.chronivaro.app;

import java.io.File;
import java.util.Objects;

public record ChronivaroAppConfig(
		boolean httpEnabled,
		String bindAddress,
		int port,
		String contextPath,
		String webResourcePath,
		String strolchPath,
		String strolchEnvironment
) {

	public static final boolean DEFAULT_HTTP_ENABLED = true;
	public static final String DEFAULT_BIND_ADDRESS = "0.0.0.0";
	public static final int DEFAULT_PORT = 9000;
	public static final String DEFAULT_CONTEXT_PATH = "/";
	public static final String DEFAULT_STROLCH_ENVIRONMENT = "dev";
	public static final String DEFAULT_STROLCH_PATH = "runtime";

	public ChronivaroAppConfig {
		Objects.requireNonNull(bindAddress, "bindAddress must not be null");
		Objects.requireNonNull(contextPath, "contextPath must not be null");
		Objects.requireNonNull(strolchEnvironment, "strolchEnvironment must not be null");
		if (port < 0 || port > 65535) {
			throw new IllegalArgumentException("Port must be between 0 and 65535, but was: " + port);
		}
		if (!contextPath.startsWith("/")) {
			contextPath = "/" + contextPath;
		}
	}

	public static ChronivaroAppConfig defaultConfig() {
		return new ChronivaroAppConfig(
				DEFAULT_HTTP_ENABLED,
				DEFAULT_BIND_ADDRESS,
				DEFAULT_PORT,
				DEFAULT_CONTEXT_PATH,
				null,
				resolveDefaultStrolchPath(),
				DEFAULT_STROLCH_ENVIRONMENT
		);
	}

	public static ChronivaroAppConfig fromArgsAndEnv(String[] args) {
		boolean httpEnabled = getBooleanPropOrEnv("chronivaro.http.enabled", "CHRONIVARO_HTTP_ENABLED", null, DEFAULT_HTTP_ENABLED);
		if (httpEnabled && getBooleanPropOrEnv("no.http", "NO_HTTP", "CHRONIVARO_NO_HTTP", false)) {
			httpEnabled = false;
		}
		String bindAddress = getStringPropOrEnv("chronivaro.bind.address", "CHRONIVARO_BIND_ADDRESS", "BIND_ADDRESS", DEFAULT_BIND_ADDRESS);
		int port = getIntPropOrEnv("chronivaro.port", "CHRONIVARO_PORT", "PORT", DEFAULT_PORT);
		String contextPath = getStringPropOrEnv("chronivaro.context.path", "CHRONIVARO_CONTEXT_PATH", "CONTEXT_PATH", DEFAULT_CONTEXT_PATH);
		String webResourcePath = getStringPropOrEnv("chronivaro.web.resource.path", "CHRONIVARO_WEB_RESOURCE_PATH", "WEB_RESOURCES_PATH", null);
		String strolchPath = getStringPropOrEnv("strolch.path", "STROLCH_PATH", null, resolveDefaultStrolchPath());
		String strolchEnvironment = getStringPropOrEnv("strolch.environment", "STROLCH_ENVIRONMENT", "STROLCH_ENV", DEFAULT_STROLCH_ENVIRONMENT);

		if (args != null) {
			for (int i = 0; i < args.length; i++) {
				String arg = args[i];
				if (("--port".equalsIgnoreCase(arg) || "-p".equalsIgnoreCase(arg)) && i + 1 < args.length) {
					port = Integer.parseInt(args[++i]);
				} else if (("--bind".equalsIgnoreCase(arg) || "-b".equalsIgnoreCase(arg)) && i + 1 < args.length) {
					bindAddress = args[++i];
				} else if ("--context-path".equalsIgnoreCase(arg) && i + 1 < args.length) {
					contextPath = args[++i];
				} else if ("--no-http".equalsIgnoreCase(arg) || "--disable-http".equalsIgnoreCase(arg)) {
					httpEnabled = false;
				} else if ("--web-resources".equalsIgnoreCase(arg) && i + 1 < args.length) {
					webResourcePath = args[++i];
				} else if (("--runtime".equalsIgnoreCase(arg) || "-r".equalsIgnoreCase(arg)) && i + 1 < args.length) {
					strolchPath = args[++i];
				} else if (("--env".equalsIgnoreCase(arg) || "-e".equalsIgnoreCase(arg)) && i + 1 < args.length) {
					strolchEnvironment = args[++i];
				}
			}
		}

		return new ChronivaroAppConfig(httpEnabled, bindAddress, port, contextPath, webResourcePath, strolchPath, strolchEnvironment);
	}

	private static String resolveDefaultStrolchPath() {
		File runtimeDir = new File("runtime");
		if (runtimeDir.isDirectory()) {
			return runtimeDir.getAbsolutePath();
		}
		File parentRuntimeDir = new File("../runtime");
		if (parentRuntimeDir.isDirectory()) {
			return parentRuntimeDir.getAbsolutePath();
		}
		return DEFAULT_STROLCH_PATH;
	}

	private static String getStringPropOrEnv(String propKey, String envKey, String fallbackEnvKey, String defaultValue) {
		String val = System.getProperty(propKey);
		if (val != null && !val.isBlank()) {
			return val;
		}
		val = System.getenv(envKey);
		if (val != null && !val.isBlank()) {
			return val;
		}
		if (fallbackEnvKey != null) {
			val = System.getenv(fallbackEnvKey);
			if (val != null && !val.isBlank()) {
				return val;
			}
		}
		return defaultValue;
	}

	private static int getIntPropOrEnv(String propKey, String envKey, String fallbackEnvKey, int defaultValue) {
		String val = System.getProperty(propKey);
		if (val != null && !val.isBlank()) {
			return Integer.parseInt(val.trim());
		}
		val = System.getenv(envKey);
		if (val != null && !val.isBlank()) {
			return Integer.parseInt(val.trim());
		}
		if (fallbackEnvKey != null) {
			val = System.getenv(fallbackEnvKey);
			if (val != null && !val.isBlank()) {
				return Integer.parseInt(val.trim());
			}
		}
		return defaultValue;
	}

	private static boolean getBooleanPropOrEnv(String propKey, String envKey, String fallbackEnvKey, boolean defaultValue) {
		String val = System.getProperty(propKey);
		if (val != null && !val.isBlank()) {
			return Boolean.parseBoolean(val.trim());
		}
		val = System.getenv(envKey);
		if (val != null && !val.isBlank()) {
			return Boolean.parseBoolean(val.trim());
		}
		if (fallbackEnvKey != null) {
			val = System.getenv(fallbackEnvKey);
			if (val != null && !val.isBlank()) {
				return Boolean.parseBoolean(val.trim());
			}
		}
		return defaultValue;
	}
}
