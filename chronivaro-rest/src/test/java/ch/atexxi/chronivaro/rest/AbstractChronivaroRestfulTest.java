package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.rest.resource.ChronivaroResource;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.rest.endpoint.AuthenticationResource;
import li.strolch.rest.filters.AuthenticationRequestFilter;
import li.strolch.testbase.runtime.RuntimeMock;
import li.strolch.utils.helper.FileHelper;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.grizzly2.servlet.GrizzlyWebContainerFactory;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.TracingConfig;
import org.glassfish.jersey.servlet.ServletProperties;
import org.glassfish.jersey.test.DeploymentContext;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.glassfish.jersey.test.spi.TestContainer;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.Collections;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public abstract class AbstractChronivaroRestfulTest extends JerseyTest {

	public static final String AUTHENTICATION_PATH = "strolch/authentication";

	private static final String RUNTIME_PATH = "target/ChronivaroRestfulTest/";
	private static final String CONFIG_SRC = "src/test/resources/runtime";
	protected static RuntimeMock runtimeMock;

	@BeforeClass
	public static void beforeClass() throws IllegalArgumentException {
		File rootPath = new File(RUNTIME_PATH);
		if (rootPath.exists())
			FileHelper.deleteFile(rootPath, true);

		runtimeMock = new RuntimeMock();
		runtimeMock.mockRuntime(RUNTIME_PATH, CONFIG_SRC);
		runtimeMock.startContainer();
	}

	@AfterClass
	public static void afterClass() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	protected String authenticate() {
		return authenticate("admin", "admin");
	}

	protected String authenticate(String username, String password) {

		// login
		JsonObject login = new JsonObject();
		login.addProperty("username", username);
		login.addProperty("password", Base64.getEncoder().encodeToString(username.getBytes()));
		Entity<String> entity = Entity.entity(login.toString(), MediaType.APPLICATION_JSON);

		JsonObject loginResult;
		try (Response result = target().path(AUTHENTICATION_PATH).request(MediaType.APPLICATION_JSON).post(entity)) {
			assertEquals(Response.Status.OK.getStatusCode(), result.getStatus());
			loginResult = JsonParser.parseString(result.readEntity(String.class)).getAsJsonObject();
		}

		assertEquals(username, loginResult.get("username").getAsString());
		assertEquals(64, loginResult.get("authToken").getAsString().length());
		assertNull(loginResult.get("msg"));

		return loginResult.get("authToken").getAsString();
	}

	@Override
	protected ResourceConfig configure() {
		forceEnable(TestProperties.LOG_TRAFFIC);
		enable(TestProperties.DUMP_ENTITY);
		return createApp();
	}

	public static ResourceConfig createApp() {
		ResourceConfig resourceConfig = new ResourceConfig();
		resourceConfig.setApplicationName("ChronivaroRestTest");
		resourceConfig.registerClasses(ChronivaroRestfulClasses.getRestfulClasses());
		resourceConfig.registerClasses(ChronivaroRestfulClasses.getProviderClasses());

		LoggingFeature loggingFeature = new LoggingFeature(
				java.util.logging.Logger.getLogger(LoggingFeature.DEFAULT_LOGGER_NAME), Level.SEVERE,
				LoggingFeature.Verbosity.PAYLOAD_ANY, null);
		resourceConfig.register(loggingFeature);
		resourceConfig.property(ServerProperties.TRACING, TracingConfig.ALL.name());
		resourceConfig.property(ServletProperties.FILTER_FORWARD_ON_404, true);
		return resourceConfig;
	}

	@Override
	protected TestContainerFactory getTestContainerFactory() throws TestContainerException {

		return new TestContainerFactory() {
			@Override
			public TestContainer create(URI baseUri, DeploymentContext deploymentContext) {
				return new TestContainer() {
					private HttpServer server;

					@Override
					public ClientConfig getClientConfig() {
						return null;
					}

					@Override
					public URI getBaseUri() {
						return baseUri;
					}

					@Override
					public void start() {
						try {
							this.server = GrizzlyWebContainerFactory.create(baseUri,
									Collections.singletonMap("jersey.config.server.provider.packages",
											"ch.atexxi.chronivaro.rest.resource"
													+ ";"
													+ AuthenticationResource.class.getPackage().getName()
													+ ";"
													+ AuthenticationRequestFilter.class.getPackage().getName()));
						} catch (ProcessingException | IOException e) {
							throw new TestContainerException(e);
						}
					}

					@Override
					public void stop() {
						if (this.server != null)
							this.server.shutdownNow();
					}
				};
			}
		};
	}
}
