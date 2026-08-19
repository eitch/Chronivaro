package ch.atexxi.chronivaro.rest.dto;

import java.time.ZonedDateTime;

public record PeriodStatusDto(
		String employeeId,
		String yearMonth,
		String status,
		ZonedDateTime submittedAt,
		ZonedDateTime approvedAt,
		String approvedBy,
		ZonedDateTime rejectedAt,
		String rejectedBy,
		String comment,
		String calculationSnapshot
) {
	public PeriodStatusDto(
			String employeeId,
			String yearMonth,
			String status,
			ZonedDateTime submittedAt,
			ZonedDateTime approvedAt,
			String approvedBy
	) {
		this(employeeId, yearMonth, status, submittedAt, approvedAt, approvedBy, null, null, null, null);
	}
}
