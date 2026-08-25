package ch.eitchnet.chronivaro.rest.dto;

import ch.eitchnet.chronivaro.core.model.WorkingLocation;
import java.time.ZonedDateTime;

public record WorkEntryDto(String id, String employeeId, ZonedDateTime start, ZonedDateTime end, int durationMinutes,
						   String source, String comment, String createdBy, WorkingLocation workingLocation,
						   boolean modified) {

	public WorkEntryDto(String id, String employeeId, ZonedDateTime start, ZonedDateTime end, int durationMinutes,
						String source, String comment, String createdBy, WorkingLocation workingLocation) {
		this(id, employeeId, start, end, durationMinutes, source, comment, createdBy, workingLocation, false);
	}
}
