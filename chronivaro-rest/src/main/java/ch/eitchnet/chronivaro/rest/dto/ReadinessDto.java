package ch.eitchnet.chronivaro.rest.dto;

import java.util.List;

public record ReadinessDto(
		String status,
		String agentState,
		List<String> activeRealms,
		String timestamp
) {
}
