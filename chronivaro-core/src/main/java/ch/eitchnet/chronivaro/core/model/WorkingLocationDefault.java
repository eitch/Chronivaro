package ch.eitchnet.chronivaro.core.model;

import java.time.DayOfWeek;

public record WorkingLocationDefault(String id, String employeeId, DayOfWeek weekday,
		WorkingLocationDurationType durationType,
		String dayPart, WorkingLocation workingLocation) {
}
