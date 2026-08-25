package ch.eitchnet.chronivaro.core.report;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public record AbsenceReportItem(String id, String employeeId, String employeeName, String absenceTypeCode,
								String absenceTypeName, LocalDate start, LocalDate end, String durationType,
								String dayPart, int minutes, String state, boolean paid, String comment,
								ZonedDateTime submittedAt, ZonedDateTime approvedAt, String approvedBy) {

	public record AbsenceReport(List<AbsenceReportItem> items) {
	}
}
