package ch.eitchnet.chronivaro.rest.dto;

import java.time.ZonedDateTime;

public record VacationAccountEntryDto(
		String id,
		String employeeId,
		ZonedDateTime date,
		String vacationType,
		int value,
		String absenceId,
		String comment,
		String createdBy,
		Integer version
) {
}
