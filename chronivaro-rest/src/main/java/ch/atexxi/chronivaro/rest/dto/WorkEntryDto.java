package ch.atexxi.chronivaro.rest.dto;

import ch.atexxi.chronivaro.core.model.WorkingLocation;
import java.time.ZonedDateTime;

public record WorkEntryDto(String id, String employeeId, ZonedDateTime start, ZonedDateTime end, int durationMinutes,
                           String source, String comment, String createdBy, WorkingLocation workingLocation) {
}
