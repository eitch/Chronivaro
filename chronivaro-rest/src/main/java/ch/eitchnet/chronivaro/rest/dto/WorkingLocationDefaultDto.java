package ch.eitchnet.chronivaro.rest.dto;

import java.time.DayOfWeek;

import ch.eitchnet.chronivaro.core.model.WorkingLocationDurationType;

public record WorkingLocationDefaultDto(String id, String employeeId, DayOfWeek weekday,
		WorkingLocationDurationType durationType,
		String dayPart, String workingLocation) {
}
