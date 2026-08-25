package ch.eitchnet.chronivaro.web;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import li.strolch.agent.api.LoggingLoader;
import li.strolch.agent.api.StrolchAgent;
import li.strolch.agent.api.StrolchBootstrapper;
import li.strolch.rest.RestfulStrolchComponent;
import li.strolch.utils.helper.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static li.strolch.agent.api.StrolchBootstrapper.ENV_STROLCH_ENVIRONMENT;

@WebListener
public class StartupListener implements ServletContextListener {

	private static final Logger logger = LoggerFactory.getLogger(StartupListener.class);
	public static final String APP_NAME = "Chronivaro";

	private StrolchAgent agent;

	@Override
	public void contextInitialized(ServletContextEvent sce) {

		logger.info("Starting " + APP_NAME + "...");

		String realPath = sce.getServletContext().getRealPath("/");
		logger.info("Real path is {}", realPath);
		long start = System.currentTimeMillis();
		try {

			StrolchBootstrapper bootstrapper = new StrolchBootstrapper(StartupListener.class);
			Optional<StrolchAgent> agentO = bootstrapper.trySetupByEnvironment(StartupListener.class);
			this.agent = agentO.orElseGet(() -> {
				logger.info("Environment var " + ENV_STROLCH_ENVIRONMENT + " not set, using bootstrap file...");
				return bootstrapper.setupByBootstrapFile(StartupListener.class);
			});

			this.agent.initialize();
			this.agent.start();
			RestfulStrolchComponent.getInstance().initialize(sce.getServletContext());
		} catch (Throwable e) {
			logger.error("Failed to start " + APP_NAME + " due to: {}", e.getMessage(), e);
			throw e;
		}

		long took = System.currentTimeMillis() - start;
		logger.info("Started " + APP_NAME + " in {}", StringHelper.formatMillisecondsDuration(took));
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		LoggingLoader.reloadLoggingConfiguration();

		if (this.agent != null) {
			logger.info("Destroying " + APP_NAME + "...");
			try {
				this.agent.stop();
				this.agent.destroy();
			} catch (Throwable e) {
				logger.error("Failed to stop " + APP_NAME + " due to: {}", e.getMessage(), e);
				throw e;
			} finally {
				logger.info("Destroyed " + APP_NAME);
				LoggingLoader.reset();
			}
		}
	}
}
