package ch.atexxi.chronivaro.rest.dto;

import java.time.DayOfWeek;

import ch.atexxi.chronivaro.core.model.WorkingLocationDurationType;

public record WorkingLocationDefaultDto(String id, String employeeId, DayOfWeek weekday,
		WorkingLocationDurationType durationType,
		String dayPart, String workingLocation) {
}
