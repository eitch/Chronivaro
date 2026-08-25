package ch.eitchnet.chronivaro.rest.providers;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

import static li.strolch.utils.helper.StringHelper.isEmpty;

@Provider
@Priority(100)
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

	public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
	public static final String PROPERTY_CORRELATION_ID = "correlationId";
	public static final String MDC_CORRELATION_ID = "correlationId";

	private static final ThreadLocal<String> CORRELATION_ID_HOLDER = new ThreadLocal<>();

	public static String getCorrelationId() {
		return CORRELATION_ID_HOLDER.get();
	}

	public static void setCorrelationId(String correlationId) {
		if (correlationId == null) {
			CORRELATION_ID_HOLDER.remove();
		} else {
			CORRELATION_ID_HOLDER.set(correlationId);
		}
	}

	public static void removeCorrelationId() {
		CORRELATION_ID_HOLDER.remove();
	}

	public static String getOrCreateCorrelationId() {
		String id = CORRELATION_ID_HOLDER.get();
		if (isEmpty(id)) {
			id = generateCorrelationId();
			CORRELATION_ID_HOLDER.set(id);
		}
		return id;
	}

	public static String generateCorrelationId() {
		return UUID.randomUUID().toString();
	}

	@Override
	public void filter(ContainerRequestContext requestContext) throws IOException {
		String correlationId = requestContext.getHeaderString(HEADER_CORRELATION_ID);
		if (isEmpty(correlationId))
			correlationId = generateCorrelationId();
		else
			correlationId = correlationId.trim();

		requestContext.setProperty(PROPERTY_CORRELATION_ID, correlationId);
		setCorrelationId(correlationId);
		MDC.put(MDC_CORRELATION_ID, correlationId);
	}

	@Override
	public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
			throws IOException {
		String correlationId = (String) requestContext.getProperty(PROPERTY_CORRELATION_ID);
		if (isEmpty(correlationId))
			correlationId = getCorrelationId();

		if (!isEmpty(correlationId))
			responseContext.getHeaders().putSingle(HEADER_CORRELATION_ID, correlationId);

		removeCorrelationId();
		MDC.remove(MDC_CORRELATION_ID);
	}
}
