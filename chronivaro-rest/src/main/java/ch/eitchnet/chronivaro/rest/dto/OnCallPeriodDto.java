package ch.eitchnet.chronivaro.rest.dto;

import java.time.LocalDate;

public record OnCallPeriodDto(
		String id,
		String employeeId,
		String employeeName,
		LocalDate startDate,
		String startTime,
		LocalDate endDate,
		String endTime,
		String comment,
		String createdBy,
		Integer version
) {
}
