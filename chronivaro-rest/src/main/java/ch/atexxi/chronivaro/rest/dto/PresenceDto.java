package ch.atexxi.chronivaro.rest.dto;

import ch.atexxi.chronivaro.core.service.PresenceService;

public record PresenceDto(String employeeId, String firstname, String lastname,
						   PresenceService.PresenceStatus status, int minutesToday) {
}
