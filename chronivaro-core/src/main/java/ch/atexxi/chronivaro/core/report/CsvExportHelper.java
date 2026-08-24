package ch.atexxi.chronivaro.core.report;

import ch.atexxi.chronivaro.core.model.*;
import li.strolch.model.Resource;

import java.time.format.DateTimeFormatter;
import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class CsvExportHelper {

	public static final String UTF8_BOM = "\uFEFF";
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	public static String escapeCsv(Object value) {
		if (value == null) {
			return "";
		}
		String str = value.toString();
		if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
			return "\"" + str.replace("\"", "\"\"") + "\"";
		}
		return str;
	}

	public static String formatDuration(int minutes) {
		int hours = Math.abs(minutes) / 60;
		int remainingMinutes = Math.abs(minutes) % 60;
		String prefix = minutes < 0 ? "-" : "";
		return String.format("%s%02d:%02d", prefix, hours, remainingMinutes);
	}

	public static String exportDayReportToCsv(DaySummary summary, Resource employee) {
		StringBuilder sb = new StringBuilder();
		sb.append(UTF8_BOM);

		String employeeId = employee != null ? employee.getId() : "";
		String employeeName = employee != null ? employee.getName() : "";

		// Summary Section
		sb.append("Date,EmployeeId,EmployeeName,TargetMinutes,ActualMinutes,HolidayMinutes,AbsenceMinutes,DayBalanceMinutes,FormattedBalance,State,WorkingLocation\n");
		sb.append(escapeCsv(summary.date())).append(",");
		sb.append(escapeCsv(employeeId)).append(",");
		sb.append(escapeCsv(employeeName)).append(",");
		sb.append(summary.targetMinutes()).append(",");
		sb.append(summary.actualMinutes()).append(",");
		sb.append(summary.holidayMinutes()).append(",");
		sb.append(summary.absenceMinutes()).append(",");
		sb.append(summary.getBalance()).append(",");
		sb.append(escapeCsv(formatDuration(summary.getBalance()))).append(",");
		sb.append(escapeCsv(summary.stateLabel())).append(",");
		sb.append(escapeCsv(summary.workingLocation() != null ? summary.workingLocation().name() : "")).append("\n\n");

		// Work Entries Section
		sb.append("WorkEntries:\n");
		sb.append("WorkEntryId,StartTime,EndTime,DurationMinutes,FormattedDuration,Source,CreatedBy,Modified\n");
		if (summary.workEntries() != null) {
			for (WorkEntryRange entry : summary.workEntries()) {
				sb.append(escapeCsv(entry.id())).append(",");
				sb.append(escapeCsv(entry.start())).append(",");
				sb.append(escapeCsv(entry.end())).append(",");
				sb.append(entry.durationMinutes()).append(",");
				sb.append(escapeCsv(formatDuration(entry.durationMinutes()))).append(",");
				sb.append(escapeCsv(entry.source() != null ? entry.source() : "")).append(",");
				sb.append(escapeCsv(entry.createdBy() != null ? entry.createdBy() : "")).append(",");
				sb.append(entry.modified()).append("\n");
			}
		}
		sb.append("\n");

		// Breaks Section
		sb.append("Breaks:\n");
		sb.append("StartTime,EndTime,DurationMinutes,FormattedDuration\n");
		if (summary.breaks() != null) {
			for (BreakRange brk : summary.breaks()) {
				sb.append(escapeCsv(brk.start())).append(",");
				sb.append(escapeCsv(brk.end())).append(",");
				sb.append(brk.durationMinutes()).append(",");
				sb.append(escapeCsv(formatDuration(brk.durationMinutes()))).append("\n");
			}
		}

		return sb.toString();
	}

	public static String exportMonthReportToCsv(MonthSummary summary, String periodState, Resource employee) {
		StringBuilder sb = new StringBuilder();
		sb.append(UTF8_BOM);

		String employeeId = employee != null ? employee.getId() : (summary != null ? summary.employeeId() : "");
		String employeeName = employee != null ? employee.getName() : "";

		// Daily Breakdown Table
		sb.append("Date,DayOfWeek,TargetMinutes,ActualMinutes,HolidayMinutes,AbsenceMinutes,DayBalanceMinutes,FormattedBalance,State\n");
		if (summary != null && summary.daySummaries() != null) {
			for (DaySummary day : summary.daySummaries()) {
				sb.append(escapeCsv(day.date())).append(",");
				sb.append(escapeCsv(day.date().getDayOfWeek().name())).append(",");
				sb.append(day.targetMinutes()).append(",");
				sb.append(day.actualMinutes()).append(",");
				sb.append(day.holidayMinutes()).append(",");
				sb.append(day.absenceMinutes()).append(",");
				sb.append(day.getBalance()).append(",");
				sb.append(escapeCsv(formatDuration(day.getBalance()))).append(",");
				sb.append(escapeCsv(day.stateLabel())).append("\n");
			}
		}
		sb.append("\n");

		// Monthly Summary Footer
		sb.append("EmployeeId,EmployeeName,YearMonth,TotalTargetMinutes,TotalActualMinutes,TotalHolidayMinutes,TotalAbsenceMinutes,InitialBalanceMinutes,PeriodBalanceMinutes,EndBalanceMinutes,FormattedEndBalance,PeriodState\n");
		if (summary != null) {
			sb.append(escapeCsv(employeeId)).append(",");
			sb.append(escapeCsv(employeeName)).append(",");
			sb.append(escapeCsv(summary.yearMonth())).append(",");
			sb.append(summary.totalTargetMinutes()).append(",");
			sb.append(summary.totalActualMinutes()).append(",");
			sb.append(summary.totalHolidayMinutes()).append(",");
			sb.append(summary.totalAbsenceMinutes()).append(",");
			sb.append(summary.initialBalanceMinutes()).append(",");
			sb.append(summary.getPeriodBalance()).append(",");
			sb.append(summary.getEndBalance()).append(",");
			sb.append(escapeCsv(formatDuration(summary.getEndBalance()))).append(",");
			sb.append(escapeCsv(periodState != null ? periodState : "OPEN")).append("\n");
		}

		return sb.toString();
	}

	public static String exportVacationReportToCsv(VacationAccountSummary summary, List<Resource> entries, Resource employee, int year) {
		StringBuilder sb = new StringBuilder();
		sb.append(UTF8_BOM);

		String employeeId = employee != null ? employee.getId() : "";
		String employeeName = employee != null ? employee.getName() : "";

		// Summary Section
		sb.append("EmployeeId,EmployeeName,Year,AnnualEntitlementMinutes,CarryOverMinutes,CorrectionsMinutes,UsageMinutes,RemainingBalanceMinutes,FormattedRemainingBalance\n");
		if (summary != null) {
			sb.append(escapeCsv(employeeId)).append(",");
			sb.append(escapeCsv(employeeName)).append(",");
			sb.append(summary.year()).append(",");
			sb.append(summary.entitlementMinutes()).append(",");
			sb.append(summary.carryOverMinutes()).append(",");
			sb.append(summary.correctionsMinutes()).append(",");
			sb.append(summary.usageMinutes()).append(",");
			sb.append(summary.remainingMinutes()).append(",");
			sb.append(escapeCsv(formatDuration(summary.remainingMinutes()))).append("\n\n");
		}

		// Journal Entries Section
		sb.append("JournalEntries:\n");
		sb.append("EntryId,Date,Type,AmountMinutes,FormattedAmount,Source,Comment,CreatedBy\n");
		if (entries != null) {
			for (Resource entry : entries) {
				String entryId = entry.getId();
				String date = entry.hasParameter(PARAM_DATE) ? entry.getDate(PARAM_DATE).toLocalDate().toString() : "";
				String type = entry.hasParameter(PARAM_VACATION_TYPE) ? entry.getString(PARAM_VACATION_TYPE) : "";
				int amount = entry.hasParameter(PARAM_VALUE) ? entry.getInteger(PARAM_VALUE) : 0;
				String source = entry.hasRelation(PARAM_ABSENCE) ? entry.getRelationId(PARAM_ABSENCE) : "";
				String comment = entry.hasParameter(PARAM_COMMENT) ? entry.getString(PARAM_COMMENT) : "";
				String createdBy = entry.hasParameter(PARAM_CREATED_BY) ? entry.getString(PARAM_CREATED_BY) : "";

				sb.append(escapeCsv(entryId)).append(",");
				sb.append(escapeCsv(date)).append(",");
				sb.append(escapeCsv(type)).append(",");
				sb.append(amount).append(",");
				sb.append(escapeCsv(formatDuration(amount))).append(",");
				sb.append(escapeCsv(source)).append(",");
				sb.append(escapeCsv(comment)).append(",");
				sb.append(escapeCsv(createdBy)).append("\n");
			}
		}

		return sb.toString();
	}

	public static String exportTeamReportToCsv(TeamReport teamReport) {
		StringBuilder sb = new StringBuilder();
		sb.append(UTF8_BOM);

		sb.append("EmployeeId,EmployeeName,TeamId,YearMonth,TargetMinutes,ActualMinutes,HolidayMinutes,AbsenceMinutes,InitialBalanceMinutes,PeriodBalanceMinutes,EndBalanceMinutes,FormattedEndBalance,PeriodState,MissingBookingsCount\n");
		if (teamReport != null && teamReport.employeeSummaries() != null) {
			for (TeamReport.TeamEmployeeSummary emp : teamReport.employeeSummaries()) {
				sb.append(escapeCsv(emp.employeeId())).append(",");
				sb.append(escapeCsv(emp.employeeName())).append(",");
				sb.append(escapeCsv(emp.teamId())).append(",");
				sb.append(escapeCsv(emp.yearMonth())).append(",");
				sb.append(emp.targetMinutes()).append(",");
				sb.append(emp.actualMinutes()).append(",");
				sb.append(emp.holidayMinutes()).append(",");
				sb.append(emp.absenceMinutes()).append(",");
				sb.append(emp.initialBalanceMinutes()).append(",");
				sb.append(emp.periodBalanceMinutes()).append(",");
				sb.append(emp.endBalanceMinutes()).append(",");
				sb.append(escapeCsv(formatDuration(emp.endBalanceMinutes()))).append(",");
				sb.append(escapeCsv(emp.periodState())).append(",");
				sb.append(emp.missingBookingsCount()).append("\n");
			}
		}

		return sb.toString();
	}

	public static String exportAbsenceReportToCsv(List<AbsenceReportItem> items) {
		StringBuilder sb = new StringBuilder();
		sb.append(UTF8_BOM);

		sb.append("AbsenceId,EmployeeId,EmployeeName,AbsenceTypeCode,AbsenceTypeName,StartDate,EndDate,DurationType,DayPart,Minutes,FormattedDuration,State,Paid,Comment,SubmittedAt,ApprovedAt,ApprovedBy\n");
		if (items != null) {
			for (AbsenceReportItem item : items) {
				sb.append(escapeCsv(item.id())).append(",");
				sb.append(escapeCsv(item.employeeId())).append(",");
				sb.append(escapeCsv(item.employeeName())).append(",");
				sb.append(escapeCsv(item.absenceTypeCode())).append(",");
				sb.append(escapeCsv(item.absenceTypeName())).append(",");
				sb.append(escapeCsv(item.start() != null ? item.start().format(DATE_FORMATTER) : "")).append(",");
				sb.append(escapeCsv(item.end() != null ? item.end().format(DATE_FORMATTER) : "")).append(",");
				sb.append(escapeCsv(item.durationType() != null ? item.durationType() : "")).append(",");
				sb.append(escapeCsv(item.dayPart() != null ? item.dayPart() : "")).append(",");
				sb.append(item.minutes()).append(",");
				sb.append(escapeCsv(formatDuration(item.minutes()))).append(",");
				sb.append(escapeCsv(item.state() != null ? item.state() : "")).append(",");
				sb.append(item.paid()).append(",");
				sb.append(escapeCsv(item.comment())).append(",");
				sb.append(escapeCsv(item.submittedAt() != null ? item.submittedAt().format(DATE_TIME_FORMATTER) : "")).append(",");
				sb.append(escapeCsv(item.approvedAt() != null ? item.approvedAt().format(DATE_TIME_FORMATTER) : "")).append(",");
				sb.append(escapeCsv(item.approvedBy())).append("\n");
			}
		}

		return sb.toString();
	}
}
