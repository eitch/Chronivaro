package ch.eitchnet.chronivaro.rest.dto;

public record VersionDto(
		String version,
		String buildTimestamp,
		String environment,
		String strolchVersion
) {
}
