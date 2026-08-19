package ch.atexxi.chronivaro.app;

import ch.atexxi.chronivaro.rest.ChronivaroRestfulClasses;
import li.strolch.agent.api.StrolchAgent;
import li.strolch.agent.api.StrolchBootstrapper;
import li.strolch.rest.RestfulStrolchComponent;
import li.strolch.utils.helper.StringHelper;
import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;

public class ChronivaroApp {

	private static final Logger logger = LoggerFactory.getLogger(ChronivaroApp.class);
	public static final String APP_NAME = "Chronivaro";

	private final ChronivaroAppConfig config;
	private StrolchAgent agent;
	private Server server;
	private int boundPort;
	private boolean running;

	public ChronivaroApp(ChronivaroAppConfig config) {
		this.config = Objects.requireNonNull(config, "config must not be null");
		this.boundPort = config.port();
	}

	public synchronized void start() throws Exception {
		if (this.running) {
			logger.warn("{} is already running", APP_NAME);
			return;
		}

		long startTime = System.currentTimeMillis();
		logger.info("Starting {} application...", APP_NAME);

		try {
			// 1. Initialize Strolch runtime
			startStrolch();

			// 2. Start Embedded HTTP server if enabled
			if (this.config.httpEnabled()) {
				startHttpServer();
			}

			this.running = true;
			long took = System.currentTimeMillis() - startTime;
			logger.info("Started {} in {}", APP_NAME, StringHelper.formatMillisecondsDuration(took));
		} catch (Throwable t) {
			logger.error("Failed to start {} due to: {}", APP_NAME, t.getMessage(), t);
			stop();
			throw t;
		}
	}

	private void startStrolch() {
		File runtimeDir = new File(this.config.strolchPath());
		logger.info("Setting up Strolch agent with runtime path: {} (environment: {})",
				runtimeDir.getAbsolutePath(), this.config.strolchEnvironment());

		StrolchBootstrapper bootstrapper = new StrolchBootstrapper(ChronivaroApp.class);
		File bootstrapFile = new File(runtimeDir, "StrolchBootstrap.xml");

		if (bootstrapFile.exists()) {
			this.agent = bootstrapper.setupByBootstrapFile(this.config.strolchEnvironment(), bootstrapFile);
		} else if (runtimeDir.exists() && runtimeDir.isDirectory()) {
			this.agent = bootstrapper.setupByRoot(this.config.strolchEnvironment(), runtimeDir);
		} else {
			Optional<StrolchAgent> agentO = bootstrapper.trySetupByEnvironment(ChronivaroApp.class);
			this.agent = agentO.orElseGet(() -> bootstrapper.setupByBootstrapFile(ChronivaroApp.class));
		}

		this.agent.initialize();
		this.agent.start();
	}

	private void startHttpServer() throws Exception {
		logger.info("Configuring Embedded Jetty on {}:{} (context: '{}')...",
				this.config.bindAddress(), this.config.port(), this.config.contextPath());

		this.server = new Server(new InetSocketAddress(this.config.bindAddress(), this.config.port()));
		this.server.setStopTimeout(5000L);

		ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
		contextHandler.setContextPath(this.config.contextPath());

		// 1. Configure static resource base for Frontend
		configureStaticResources(contextHandler);

		// 2. Configure Jersey / JAX-RS Servlet Container
		ResourceConfig resourceConfig = createResourceConfig();
		ServletContainer jerseyServlet = new ServletContainer(resourceConfig);
		ServletHolder jerseyHolder = new ServletHolder("jersey-rest", jerseyServlet);
		contextHandler.addServlet(jerseyHolder, "/rest/*");

		this.server.setHandler(contextHandler);

		// Initialize RestfulStrolchComponent with the ServletContext before server starts
		RestfulStrolchComponent.getInstance().initialize(contextHandler.getServletContext());

		// Start Jetty server
		this.server.start();

		for (Connector connector : this.server.getConnectors()) {
			if (connector instanceof ServerConnector serverConnector) {
				this.boundPort = serverConnector.getLocalPort();
				logger.info("Jetty bound to port {}", this.boundPort);
			}
		}

		logger.info("REST API and Web UI initialized successfully on {}:{}", this.config.bindAddress(), this.boundPort);
	}

	private void configureStaticResources(ServletContextHandler contextHandler) throws Exception {
		ResourceFactory resourceFactory = ResourceFactory.of(contextHandler);

		if (this.config.webResourcePath() != null && new File(this.config.webResourcePath()).exists()) {
			File resourceDir = new File(this.config.webResourcePath());
			logger.info("Serving static web resources from directory: {}", resourceDir.getAbsolutePath());
			contextHandler.setBaseResourceAsPath(resourceDir.toPath());
		} else {
			URL webappUrl = getClass().getResource("/webapp");
			if (webappUrl != null) {
				logger.info("Serving static web resources from classpath: {}", webappUrl);
				Resource classPathRes = resourceFactory.newClassLoaderResource("webapp");
				if (classPathRes != null && classPathRes.exists()) {
					contextHandler.setBaseResource(classPathRes);
				}
			} else {
				File localWebapp = new File("chronivaro-web/src/main/webapp");
				if (localWebapp.exists()) {
					logger.info("Serving static web resources from relative path: {}", localWebapp.getAbsolutePath());
					contextHandler.setBaseResourceAsPath(localWebapp.toPath());
				} else {
					logger.warn("No static web resources directory found for web UI");
				}
			}
		}

		ServletHolder defaultServlet = new ServletHolder("default", DefaultServlet.class);
		defaultServlet.setInitParameter("dirAllowed", "false");
		contextHandler.addServlet(defaultServlet, "/");
		contextHandler.setWelcomeFiles(new String[]{"index.html"});
	}

	private ResourceConfig createResourceConfig() {
		ResourceConfig resourceConfig = new ResourceConfig();
		resourceConfig.setApplicationName(APP_NAME);

		// Register REST resource endpoints
		for (Class<?> clazz : ChronivaroRestfulClasses.getRestfulClasses()) {
			resourceConfig.register(clazz);
		}

		// Register providers / mappers
		for (Class<?> clazz : ChronivaroRestfulClasses.getProviderClasses()) {
			resourceConfig.register(clazz);
		}

		return resourceConfig;
	}

	public synchronized void stop() {
		if (!this.running && this.server == null && this.agent == null) {
			return;
		}

		logger.info("Stopping {}...", APP_NAME);

		if (this.server != null) {
			try {
				this.server.stop();
				this.server.destroy();
				logger.info("HTTP Server stopped");
			} catch (Exception e) {
				logger.error("Failed to stop HTTP server cleanly: {}", e.getMessage(), e);
			} finally {
				this.server = null;
			}
		}

		if (this.agent != null) {
			try {
				this.agent.stop();
				this.agent.destroy();
				logger.info("Strolch Agent destroyed");
			} catch (Exception e) {
				logger.error("Failed to stop Strolch Agent cleanly: {}", e.getMessage(), e);
			} finally {
				this.agent = null;
			}
		}

		this.running = false;
		logger.info("Stopped {}", APP_NAME);
	}

	public boolean isRunning() {
		return this.running;
	}

	public StrolchAgent getAgent() {
		return this.agent;
	}

	public int getPort() {
		return this.boundPort;
	}

	public ChronivaroAppConfig getConfig() {
		return this.config;
	}

	public Thread registerShutdownHook() {
		Thread hook = new Thread(() -> {
			logger.info("Shutdown hook triggered, stopping {}...", APP_NAME);
			stop();
		}, APP_NAME + "-shutdown-hook");
		Runtime.getRuntime().addShutdownHook(hook);
		return hook;
	}

	public static void main(String[] args) {
		ChronivaroAppConfig config = ChronivaroAppConfig.fromArgsAndEnv(args);
		ChronivaroApp app = new ChronivaroApp(config);

		app.registerShutdownHook();

		try {
			app.start();
			if (config.httpEnabled() && app.server != null) {
				app.server.join();
			}
		} catch (Throwable t) {
			logger.error("Fatal error running Chronivaro: {}", t.getMessage(), t);
			System.exit(1);
		}
	}
}
