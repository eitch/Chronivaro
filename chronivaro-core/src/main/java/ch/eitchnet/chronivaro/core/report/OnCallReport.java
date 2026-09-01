package ch.eitchnet.chronivaro.core.report;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public record OnCallReport(String context,
						   LocalDate from,
						   LocalDate to,
						   List<OnCallPeriodItem> periods,
						   List<OnCallWorkEntryItem> workEntries,
						   int totalPeriodsCount,
						   int totalWorkEntriesCount,
						   int totalWorkEntryMinutes) {

	public record OnCallPeriodItem(String id,
								   String employeeId,
								   String employeeName,
								   LocalDate startDate,
								   String startTime,
								   LocalDate endDate,
								   String endTime,
								   String comment,
								   String createdBy) {
	}

	public record OnCallWorkEntryItem(String id,
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
