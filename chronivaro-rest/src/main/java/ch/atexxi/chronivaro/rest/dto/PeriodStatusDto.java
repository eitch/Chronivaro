package ch.atexxi.chronivaro.rest.dto;

import java.time.ZonedDateTime;

public record PeriodStatusDto(
		String employeeId,
		String yearMonth,
		String status,
		ZonedDateTime submittedAt,
		ZonedDateTime approvedAt,
		String approvedBy
) {
}
