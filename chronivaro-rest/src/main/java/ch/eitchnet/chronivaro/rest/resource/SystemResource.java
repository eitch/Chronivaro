package ch.eitchnet.chronivaro.rest.resource;

import ch.eitchnet.chronivaro.rest.dto.BrandingDto;
import ch.eitchnet.chronivaro.rest.dto.ChronivaroMapper;
import ch.eitchnet.chronivaro.rest.dto.HealthDto;
import ch.eitchnet.chronivaro.rest.dto.ReadinessDto;
import ch.eitchnet.chronivaro.rest.dto.SystemMetricsDto;
import ch.eitchnet.chronivaro.rest.dto.VersionDto;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.agent.api.ComponentContainer;
import li.strolch.agent.api.StrolchAgent;
import li.strolch.agent.api.VersionQueryResult;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.rest.RestfulStrolchComponent;
import li.strolch.utils.iso8601.ISO8601;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;

@Path("chronivaro/v1")
public class SystemResource {

	@GET
	@Path("system/health")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getHealth() {
		StrolchAgent agent = getAgent();
		boolean isRunning = agent != null && agent.getContainer() != null && agent.getContainer().getState().isStarted();
		String status = isRunning ? "UP" : "DOWN";
		String agentState = agent != null && agent.getContainer() != null ? agent.getContainer().getState().name() : "STOPPED";
		long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
		String timestamp = ISO8601.toString(new Date());

		HealthDto dto = new HealthDto(status, agentState, uptime, timestamp);
		return Response.ok(ChronivaroRestHelper.createGson().toJson(dto), MediaType.APPLICATION_JSON).build();
	}

	@GET
	@Path("system/readiness")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getReadiness() {
		StrolchAgent agent = getAgent();
		boolean isReady = agent != null && agent.getContainer() != null && agent.getContainer().getState().isStarted();
		List<String> realms = agent != null ? new ArrayList<>(agent.getRealmNames()) : List.of();
		String status = isReady ? "READY" : "NOT_READY";
		String agentState = agent != null && agent.getContainer() != null ? agent.getContainer().getState().name() : "STOPPED";
		String timestamp = ISO8601.toString(new Date());

		ReadinessDto dto = new ReadinessDto(status, agentState, realms, timestamp);
		if (isReady) {
			return Response.ok(ChronivaroRestHelper.createGson().toJson(dto), MediaType.APPLICATION_JSON).build();
		} else {
			return Response.status(Response.Status.SERVICE_UNAVAILABLE)
					.entity(ChronivaroRestHelper.createGson().toJson(dto))
					.type(MediaType.APPLICATION_JSON)
					.build();
		}
	}

	@GET
	@Path("version")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getVersionRoot() {
		return getVersion();
	}

	@GET
	@Path("branding")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getBrandingRoot() {
		return getBranding();
	}

	@GET
	@Path("branding/logo")
	@Produces({"image/png", "image/jpeg", "image/svg+xml", "image/gif", "image/webp", "image/x-icon", "*/*"})
	public Response getBrandingLogoRoot() {
		return getBrandingLogo();
	}

	@GET
	@Path("system/branding/logo")
	@Produces({"image/png", "image/jpeg", "image/svg+xml", "image/gif", "image/webp", "image/x-icon", "*/*"})
	public Response getBrandingLogo() {
		StrolchAgent agent = getAgent();
		if (agent != null) {
			try {
				String logo = agent.runAsAgentWithResult(ctx -> {
					try (StrolchTransaction tx = agent.openTx(ctx.getCertificate(), "GetBrandingLogo", true)) {
						Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", false);
						if (config != null && config.hasParameter(PARAM_COMPANY_LOGO)) {
							return config.getString(PARAM_COMPANY_LOGO);
						}
						return "";
					}
				});
				if (logo != null && !logo.isBlank()) {
					String trimmed = logo.trim();
					if (trimmed.startsWith("data:")) {
						int semicolonIdx = trimmed.indexOf(";base64,");
						if (semicolonIdx > 5) {
							String mimeType = trimmed.substring(5, semicolonIdx).trim();
							String base64Payload = trimmed.substring(semicolonIdx + 8);
							byte[] imageBytes = Base64.getDecoder().decode(base64Payload);
							return Response.ok(imageBytes, mimeType)
									.header("Cache-Control", "public, max-age=3600")
									.build();
						}
					} else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
						return Response.temporaryRedirect(URI.create(trimmed)).build();
					}
				}
			} catch (Exception ignored) {
			}
		}
		return Response.status(Response.Status.NOT_FOUND).build();
	}

	@GET
	@Path("system/branding")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getBranding() {
		StrolchAgent agent = getAgent();
		if (agent != null) {
			try {
				BrandingDto dto = agent.runAsAgentWithResult(ctx -> {
					try (StrolchTransaction tx = agent.openTx(ctx.getCertificate(), "GetBranding", true)) {
						Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", false);
						return ChronivaroMapper.brandingToDto(config);
					}
				});
				if (dto != null) {
					return Response.ok(ChronivaroRestHelper.createGson().toJson(dto), MediaType.APPLICATION_JSON).build();
				}
			} catch (Exception ignored) {
			}
		}
		BrandingDto fallback = new BrandingDto(DEFAULT_COMPANY_NAME, "", DEFAULT_LANGUAGE);
		return Response.ok(ChronivaroRestHelper.createGson().toJson(fallback), MediaType.APPLICATION_JSON).build();
	}

	@GET
	@Path("system/version")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getVersion() {
		StrolchAgent agent = getAgent();
		String version = "0.1.0-SNAPSHOT";
		String buildTimestamp = ISO8601.toString(new Date());
		String environment = agent != null ? agent.getEnvironment() : "development";
		String strolchVersion = "unknown";

		if (agent != null) {
			try {
				VersionQueryResult versionResult = agent.getVersion();
				if (versionResult != null && versionResult.getAppVersion() != null) {
					version = versionResult.getAppVersion().getArtifactVersion();
					buildTimestamp = versionResult.getAppVersion().getBuildTimestamp();
				}
				if (versionResult != null && versionResult.getAgentVersion() != null) {
					strolchVersion = versionResult.getAgentVersion().getArtifactVersion();
				}
			} catch (Exception ignored) {
			}
		}

		VersionDto dto = new VersionDto(version, buildTimestamp, environment, strolchVersion);
		return Response.ok(ChronivaroRestHelper.createGson().toJson(dto), MediaType.APPLICATION_JSON).build();
	}

	@GET
	@Path("system/metrics")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMetrics() {
		Runtime runtime = Runtime.getRuntime();
		long totalMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();
		long maxMemory = runtime.maxMemory();
		long usedMemory = totalMemory - freeMemory;

		MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
		long nonHeapUsed = memoryMXBean.getNonHeapMemoryUsage().getUsed();

		ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
		int activeThreads = threadMXBean.getThreadCount();
		int peakThreads = threadMXBean.getPeakThreadCount();
		long totalStartedThreads = threadMXBean.getTotalStartedThreadCount();

		OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
		int availableProcessors = osMXBean.getAvailableProcessors();
		double systemLoadAverage = osMXBean.getSystemLoadAverage();

		long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
		String timestamp = ISO8601.toString(new Date());

		SystemMetricsDto dto = new SystemMetricsDto(
				usedMemory,
				maxMemory,
				freeMemory,
				nonHeapUsed,
				activeThreads,
				peakThreads,
				totalStartedThreads,
				availableProcessors,
				systemLoadAverage,
				uptime,
				timestamp
		);
		return Response.ok(ChronivaroRestHelper.createGson().toJson(dto), MediaType.APPLICATION_JSON).build();
	}

	private StrolchAgent getAgent() {
		try {
			return RestfulStrolchComponent.getInstance().getAgent();
		} catch (Exception e) {
			return null;
		}
	}
}
