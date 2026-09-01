package ch.eitchnet.chronivaro.rest.dto;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public record OnCallReportDto(String context,
							  LocalDate from,
							  LocalDate to,
							  List<OnCallPeriodItemDto> periods,
							  List<OnCallWorkEntryItemDto> workEntries,
							  int totalPeriodsCount,
							  int totalWorkEntriesCount,
							  int totalWorkEntryMinutes) {

	public record OnCallPeriodItemDto(String id,
									  String employeeId,
									  String employeeName,
									  LocalDate startDate,
									  String startTime,
									  LocalDate endDate,
									  String endTime,
									  String comment,
									  String createdBy) {
	}

	public record OnCallWorkEntryItemDto(String id,
										 String employeeId,
										 String employeeName,
										 LocalDate date,
										 ZonedDateTime start,
										 ZonedDateTime end,
										 int durationMinutes,
										 String source,
										 String comment,
										 String createdBy,
										 boolean modified) {
	}
}
