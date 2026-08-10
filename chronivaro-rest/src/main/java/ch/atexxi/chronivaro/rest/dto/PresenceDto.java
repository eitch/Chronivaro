package ch.atexxi.chronivaro.rest.dto;

import ch.atexxi.chronivaro.core.service.PresenceService;

public record PresenceDto(String employeeId, String displayName, PresenceService.PresenceStatus status,
                           int minutesToday) {
}
