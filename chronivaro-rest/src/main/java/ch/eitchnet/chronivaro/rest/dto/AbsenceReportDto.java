package ch.eitchnet.chronivaro.rest.dto;

import java.util.List;

public record AbsenceReportDto(List<AbsenceReportItemDto> items) {

	public record AbsenceReportItemDto(String id, String employeeId, String employeeName, String absenceTypeCode,
									   String absenceTypeName, String start, String end, String durationType,
									   String dayPart, int minutes, String state, boolean paid, String comment,
									   String createdAt, String approvedAt, String approvedBy) {
	}
}
