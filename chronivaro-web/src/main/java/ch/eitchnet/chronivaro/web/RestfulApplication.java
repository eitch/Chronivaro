package ch.eitchnet.chronivaro.web;

import ch.eitchnet.chronivaro.rest.ChronivaroRestfulClasses;
import jakarta.ws.rs.ApplicationPath;
import li.strolch.rest.RestfulStrolchComponent;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.logging.Level;

import static ch.eitchnet.chronivaro.web.StartupListener.APP_NAME;

@ApplicationPath("rest")
public class RestfulApplication extends ResourceConfig {

	private static final Logger logger = LoggerFactory.getLogger(RestfulApplication.class);

	public RestfulApplication() {
		setApplicationName(APP_NAME);

		// register resources
		for (Class<?> clazz : ChronivaroRestfulClasses.getRestfulClasses()) {
			register(clazz);
		}

		// filters
		// log exceptions and return them as plain text to the caller
		// the JSON generated is in UTF-8
		for (Class<?> clazz : ChronivaroRestfulClasses.getProviderClasses()) {
			register(clazz);
		}

		RestfulStrolchComponent restfulComponent = RestfulStrolchComponent.getInstance();
		if (restfulComponent.isRestLoggingEntity()) {
			register(new LoggingFeature(java.util.logging.Logger.getLogger(LoggingFeature.DEFAULT_LOGGER_NAME),
					Level.SEVERE, LoggingFeature.Verbosity.PAYLOAD_ANY, null));

			property(ServerProperties.TRACING, "ALL");
			property(ServerProperties.TRACING_THRESHOLD, "TRACE");
		}

		logger.info("Initialized REST application {} with {} classes, {} instances, {} resources and {} properties",
				getApplicationName(), getClasses().size(), getInstances().size(), getResources().size(),
				getProperties().size());
	}
}
