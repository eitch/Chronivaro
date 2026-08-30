package ch.eitchnet.chronivaro.rest.dto;

import ch.eitchnet.chronivaro.core.service.PresenceService;

	public record PresenceDto(String employeeId, String firstname, String lastname, String teamId, String teamName,
							   PresenceService.PresenceStatus status, String statusLabel, int minutesToday,
							   String absenceTypeCode, String absenceTypeName, boolean isOff, String workingLocation,
							   boolean isPreviousDayTimer, String timerStartDate) {
}
