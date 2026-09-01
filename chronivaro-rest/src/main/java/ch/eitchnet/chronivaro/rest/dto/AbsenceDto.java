package ch.eitchnet.chronivaro.rest.dto;

import java.time.ZonedDateTime;

public record AbsenceDto(String id, String employeeId, String employeeName, String personalNumber, String teamName,
						 String absenceTypeCode, String absenceTypeName, ZonedDateTime start, ZonedDateTime end,
						 String durationType, String dayPart, Integer minutes, String comment, String state,
						 String createdBy) {
	public AbsenceDto(String id, String employeeId, String employeeName, String personalNumber, String teamName,
			String absenceTypeCode, String absenceTypeName, ZonedDateTime start, ZonedDateTime end,
			String durationType, String dayPart, Integer minutes, String comment, String state) {
		this(id, employeeId, employeeName, personalNumber, teamName, absenceTypeCode, absenceTypeName, start, end,
				durationType, dayPart, minutes, comment, state, null);
	}

	public AbsenceDto(String id, String employeeId, String absenceTypeCode, ZonedDateTime start, ZonedDateTime end,
			String durationType, String dayPart, Integer minutes, String comment, String state) {
		this(id, employeeId, null, null, null, absenceTypeCode, null, start, end, durationType, dayPart, minutes,
				comment, state, null);
	}
}
