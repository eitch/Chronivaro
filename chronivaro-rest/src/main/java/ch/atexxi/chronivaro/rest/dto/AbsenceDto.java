package ch.atexxi.chronivaro.rest.dto;

import java.time.ZonedDateTime;

public record AbsenceDto(
		String id,
		String employeeId,
		String absenceTypeCode,
		ZonedDateTime start,
		ZonedDateTime end,
		String durationType,
		String dayPart,
		Integer minutes,
		String comment,
		String state
) {}
