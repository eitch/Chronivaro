package ch.atexxi.chronivaro.rest.dto;

import java.time.ZonedDateTime;

public record AuditLogDto(String id, ZonedDateTime timestamp, String username, String action, String entityType,
		String entityId, String details) {
}
