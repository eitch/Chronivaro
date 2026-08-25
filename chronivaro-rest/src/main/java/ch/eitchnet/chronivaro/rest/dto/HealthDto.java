package ch.eitchnet.chronivaro.rest.dto;

public record HealthDto(
		String status,
		String agentState,
		long uptimeMs,
		String timestamp
) {
}
