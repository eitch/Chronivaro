package ch.atexxi.chronivaro.rest.dto;

import java.time.ZonedDateTime;

public record PeriodStatusDto(
		String employeeId,
		String employeeName,
		String personalNumber,
		String teamName,
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
		this(employeeId, null, null, null, yearMonth, status, submittedAt, approvedAt, approvedBy, null, null, null, null);
	}

	public PeriodStatusDto(
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
		this(employeeId, null, null, null, yearMonth, status, submittedAt, approvedAt, approvedBy, rejectedAt, rejectedBy, comment, calculationSnapshot);
	}
}
