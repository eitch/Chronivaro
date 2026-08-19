package ch.atexxi.chronivaro.rest.dto;

import ch.atexxi.chronivaro.core.model.DaySummary;
import ch.atexxi.chronivaro.core.model.MonthSummary;
import ch.atexxi.chronivaro.core.service.PresenceService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import ch.atexxi.chronivaro.core.model.WorkingLocation;

import java.time.Duration;
import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class ChronivaroMapper {

	public static PresenceDto toDto(PresenceService.PresenceInfo info) {
		return new PresenceDto(info.employeeId(), info.firstname(), info.lastname(), info.teamId(), info.teamName(),
				info.status(), info.statusLabel(), info.minutesToday(), info.absenceTypeCode(), info.absenceTypeName(),
				info.isOff(), info.workingLocation());
	}

	public static WorkEntryDto toDto(Resource workEntry) {
		ZonedDateTime start = workEntry.getDate(PARAM_START);
		ZonedDateTime end = workEntry.getDate(PARAM_END);
		boolean isActive = end.getYear() == 1970;

		int durationMinutes;
		if (isActive) {
			durationMinutes = (int) Duration.between(start, ZonedDateTime.now(start.getZone())).toMinutes();
			end = null;
		} else {
			durationMinutes = (int) Duration.between(start, end).toMinutes();
		}

		return new WorkEntryDto(workEntry.getId(), workEntry.getRelationId(PARAM_EMPLOYEE), start, end, durationMinutes,
				workEntry.getString(PARAM_SOURCE), workEntry.getString(PARAM_COMMENT),
				workEntry.getString(PARAM_CREATED_BY), WorkingLocation.fromValue(workEntry.getString(PARAM_WORKING_LOCATION)));
	}

	public static AbsenceDto toDto(Resource absence, String absenceTypeCode) {
		return new AbsenceDto(absence.getId(), absence.getRelationId(PARAM_EMPLOYEE), absenceTypeCode,
				absence.getDate(PARAM_START), absence.getDate(PARAM_END), absence.getString(PARAM_DURATION_TYPE),
				absence.hasParameter(PARAM_DAY_PART) ? absence.getString(PARAM_DAY_PART) : null,
				absence.hasParameter(PARAM_MINUTES) ? absence.getInteger(PARAM_MINUTES) : null,
				absence.getString(PARAM_COMMENT), absence.getString(PARAM_STATE));
	}

	public static DaySummaryDto toDto(DaySummary summary) {
		return new DaySummaryDto(summary.date(), summary.state(), summary.stateLabel(), summary.targetMinutes(),
				summary.actualMinutes(), summary.holidayMinutes(), summary.absenceMinutes(), summary.isOff(),
				summary.getBalance(), summary.workingLocation(), summary
						.workEntries()
						.stream()
						.map(e -> new WorkEntryRangeDto(e.id(), e.start(), e.end(), e.durationMinutes()))
						.toList(), summary
				.breaks()
				.stream()
				.map(b -> new BreakRangeDto(b.start(), b.end(), b.durationMinutes()))
				.toList());
	}

	public static MonthSummaryDto toDto(MonthSummary summary) {
		return new MonthSummaryDto(summary.employeeId(), summary.yearMonth(), summary.totalTargetMinutes(),
				summary.totalActualMinutes(), summary.totalHolidayMinutes(), summary.totalAbsenceMinutes(),
				summary.initialBalanceMinutes(), summary.getPeriodBalance(), summary.getEndBalance(),
				summary.daySummaries().stream().map(ChronivaroMapper::toDto).toList());
	}

	public static TeamDto teamToDto(Resource team) {
		return new TeamDto(team.getId(), team.getString(PARAM_NAME));
	}

	public static LocationDto locationToDto(StrolchTransaction tx, Resource location) {
		String holidayCalendarId = location.getRelationId(PARAM_HOLIDAY_CALENDAR);
		String holidayCalendarName = null;
		if (holidayCalendarId != null) {
			Resource calendar = tx.getResourceBy(TYPE_HOLIDAY_CALENDAR, holidayCalendarId);
			if (calendar != null)
				holidayCalendarName = calendar.getName();
		}
		return new LocationDto(location.getId(), location.getString(PARAM_NAME), location.getString(PARAM_TIMEZONE),
				holidayCalendarId, holidayCalendarName);
	}

	public static AbsenceTypeDto absenceTypeToDto(Resource type) {
		return new AbsenceTypeDto(type.getId(), type.getString(PARAM_CODE), type.getString(PARAM_NAME),
				type.getBoolean(PARAM_COUNT_AS_TARGET_TIME), type.getBoolean(PARAM_REDUCE_VACATION_CREDIT),
				type.getBoolean(PARAM_PAID), type.getBoolean(PARAM_APPROVAL_REQUIRED),
				type.getStringList(PARAM_DURATION_TYPES), type.getBoolean(PARAM_ACTIVE));
	}

	public static EmployeeDto employeeToDto(StrolchTransaction tx, Resource employee) {
		String teamId = employee.getRelationId(PARAM_PRIMARY_TEAM);
		String teamName = null;
		if (teamId != null) {
			Resource team = tx.getResourceBy(TYPE_TEAM, teamId);
			if (team != null)
				teamName = team.getName();
		}

		String locationId = employee.getRelationId(PARAM_LOCATION);
		String locationName = null;
		if (locationId != null) {
			Resource location = tx.getResourceBy(TYPE_LOCATION, locationId);
			if (location != null)
				locationName = location.getName();
		}

		return new EmployeeDto(employee.getId(), employee.getString(PARAM_PERSONAL_NUMBER),
				employee.getString(PARAM_FIRSTNAME), employee.getString(PARAM_LASTNAME),
				employee.hasParameter(PARAM_BIRTHDATE) ? employee.getDate(PARAM_BIRTHDATE).toLocalDate() : null, teamId,
				teamName, locationId, locationName, employee.getString(PARAM_TIMEZONE),
				employee.getDate(PARAM_JOIN_DATE).toLocalDate(),
				employee.hasParameter(PARAM_EXIT_DATE) && employee.getDate(PARAM_EXIT_DATE) != null ?
						employee.getDate(PARAM_EXIT_DATE).toLocalDate() : null, employee.getBoolean(PARAM_ACTIVE),
				employee.getString(PARAM_USER_ID), employee.getString(PARAM_USERNAME), employee.getString(PARAM_EMAIL),
				null);
	}

	public static HolidayCalendarDto holidayCalendarToDto(Resource calendar) {
		return new HolidayCalendarDto(calendar.getId(), calendar.getString(PARAM_NAME));
	}

	public static HolidayDto holidayToDto(Resource holiday) {
		return new HolidayDto(holiday.getId(), holiday.getRelationId(PARAM_HOLIDAY_CALENDAR),
				holiday.getDate(PARAM_DATE).toLocalDate(), holiday.getString(PARAM_NAME),
				holiday.getDouble(PARAM_CREDIT_FACTOR));
	}

	public static ScheduleDto scheduleToDto(Resource schedule) {
		return new ScheduleDto(schedule.getId(), schedule.getRelationId(PARAM_EMPLOYEE),
				schedule.getDate(PARAM_VALID_FROM),
				schedule.hasParameter(PARAM_VALID_TO) ? schedule.getDate(PARAM_VALID_TO) : null,
				schedule.getInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY),
				schedule.getInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY),
				schedule.getInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY),
				schedule.getInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY),
				schedule.getInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY),
				schedule.getInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY),
				schedule.getInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY));
	}

	public static ScheduleTemplateDto scheduleTemplateToDto(Resource template) {
		return new ScheduleTemplateDto(template.getId(), template.getString(PARAM_NAME),
				template.getInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY),
				template.getInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY),
				template.getInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY),
				template.getInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY),
				template.getInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY),
				template.getInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY),
				template.getInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY));
	}

	public static AuditLogDto auditLogToDto(Resource auditEvent) {
		String details = auditEvent.getString(PARAM_DETAILS);
		if (details == null || details.isEmpty()) {
			details = auditEvent.getString(PARAM_REASON);
		}
		return new AuditLogDto(auditEvent.getId(), auditEvent.getDate(PARAM_DATE),
				auditEvent.getString(PARAM_CREATED_BY), auditEvent.getString(PARAM_ACTION),
				auditEvent.getString(PARAM_ELEMENT_TYPE), auditEvent.getString(PARAM_ELEMENT_ID),
				details != null ? details : "");
	}

	public static PeriodStatusDto periodToDto(Resource period) {
		ZonedDateTime submittedAt = period.hasParameter(PARAM_SUBMITTED_AT)
				&& period.getDate(PARAM_SUBMITTED_AT).getYear() > 1970
				? period.getDate(PARAM_SUBMITTED_AT) : null;
		ZonedDateTime approvedAt = period.hasParameter(PARAM_APPROVED_AT)
				&& period.getDate(PARAM_APPROVED_AT).getYear() > 1970
				? period.getDate(PARAM_APPROVED_AT) : null;
		String approvedBy = period.hasParameter(PARAM_APPROVED_BY)
				&& !period.getString(PARAM_APPROVED_BY).isEmpty()
				? period.getString(PARAM_APPROVED_BY) : null;
		ZonedDateTime rejectedAt = period.hasParameter(PARAM_REJECTED_AT)
				&& period.getDate(PARAM_REJECTED_AT).getYear() > 1970
				? period.getDate(PARAM_REJECTED_AT) : null;
		String rejectedBy = period.hasParameter(PARAM_REJECTED_BY)
				&& !period.getString(PARAM_REJECTED_BY).isEmpty()
				? period.getString(PARAM_REJECTED_BY) : null;
		String comment = period.hasParameter(PARAM_COMMENT)
				&& !period.getString(PARAM_COMMENT).isEmpty()
				? period.getString(PARAM_COMMENT) : null;
		String calculationSnapshot = period.hasParameter(PARAM_CALCULATION_SNAPSHOT)
				&& !period.getString(PARAM_CALCULATION_SNAPSHOT).isEmpty()
				? period.getString(PARAM_CALCULATION_SNAPSHOT) : null;
		return new PeriodStatusDto(
				period.getRelationId(PARAM_EMPLOYEE),
				period.getString(PARAM_YEAR_MONTH),
				period.getString(PARAM_STATE),
				submittedAt,
				approvedAt,
				approvedBy,
				rejectedAt,
				rejectedBy,
				comment,
				calculationSnapshot);
	}

	public static VacationAccountEntryDto vacationEntryToDto(Resource entry) {
		String absenceId = entry.getRelationId(PARAM_ABSENCE);
		String comment = entry.hasParameter(PARAM_COMMENT) ? entry.getString(PARAM_COMMENT) : null;
		String createdBy = entry.hasParameter(PARAM_CREATED_BY) ? entry.getString(PARAM_CREATED_BY) : null;
		Integer version = entry.hasParameter(PARAM_VERSION) ? entry.getInteger(PARAM_VERSION) : null;
		return new VacationAccountEntryDto(
				entry.getId(),
				entry.getRelationId(PARAM_EMPLOYEE),
				entry.getDate(PARAM_DATE),
				entry.getString(PARAM_VACATION_TYPE),
				entry.getInteger(PARAM_VALUE),
				absenceId,
				comment,
				createdBy,
				version);
	}

	public static VacationAccountSummaryDto vacationSummaryToDto(ch.atexxi.chronivaro.core.model.VacationAccountSummary summary,
			java.util.List<Resource> entries) {
		java.util.List<VacationAccountEntryDto> entryDtos = entries != null
				? entries.stream().map(ChronivaroMapper::vacationEntryToDto).toList()
				: java.util.List.of();
		return new VacationAccountSummaryDto(
				summary.employeeId(),
				summary.year(),
				summary.carryOverMinutes(),
				summary.entitlementMinutes(),
				summary.correctionsMinutes(),
				summary.usageMinutes(),
				summary.remainingMinutes(),
				entryDtos);
	}

	public static TeamReportDto teamReportToDto(ch.atexxi.chronivaro.core.report.TeamReport report) {
		java.util.List<TeamReportDto.TeamEmployeeSummaryDto> emps = report.employeeSummaries() != null
				? report.employeeSummaries().stream().map(e -> new TeamReportDto.TeamEmployeeSummaryDto(
						e.employeeId(),
						e.employeeName(),
						e.teamId(),
						e.yearMonth().toString(),
						e.targetMinutes(),
						e.actualMinutes(),
						e.holidayMinutes(),
						e.absenceMinutes(),
						e.initialBalanceMinutes(),
						e.periodBalanceMinutes(),
						e.endBalanceMinutes(),
						e.periodState(),
						e.missingBookingsCount()
				)).toList()
				: java.util.List.of();

		return new TeamReportDto(report.teamId(), report.teamName(), report.yearMonth().toString(), emps);
	}

	public static AbsenceReportDto absenceReportToDto(java.util.List<ch.atexxi.chronivaro.core.report.AbsenceReportItem> items) {
		java.util.List<AbsenceReportDto.AbsenceReportItemDto> itemDtos = items != null
				? items.stream().map(item -> new AbsenceReportDto.AbsenceReportItemDto(
						item.id(),
						item.employeeId(),
						item.employeeName(),
						item.absenceTypeCode(),
						item.absenceTypeName(),
						item.start() != null ? item.start().toString() : null,
						item.end() != null ? item.end().toString() : null,
						item.durationType(),
						item.dayPart(),
						item.minutes(),
						item.state(),
						item.paid(),
						item.comment(),
						item.submittedAt() != null ? item.submittedAt().toString() : null,
						item.approvedAt() != null ? item.approvedAt().toString() : null,
						item.approvedBy()
				)).toList()
				: java.util.List.of();

		return new AbsenceReportDto(itemDtos);
	}

	public static ConfigurationDto configurationToDto(Resource config) {
		return new ConfigurationDto(
				config.hasParameter(PARAM_WEEKLY_TARGET_MINUTES) ? config.getInteger(PARAM_WEEKLY_TARGET_MINUTES) : DEFAULT_WEEKLY_TARGET_MINUTES,
				config.hasParameter(PARAM_ANNUAL_VACATION_DAYS) ? config.getInteger(PARAM_ANNUAL_VACATION_DAYS) : DEFAULT_ANNUAL_VACATION_DAYS,
				config.hasParameter(PARAM_MINUTES_PER_VACATION_DAY) ? config.getInteger(PARAM_MINUTES_PER_VACATION_DAY) : DEFAULT_MINUTES_PER_VACATION_DAY,
				config.hasParameter(PARAM_VACATION_ABSENCE_TYPE_CODE) ? config.getString(PARAM_VACATION_ABSENCE_TYPE_CODE) : DEFAULT_VACATION_ABSENCE_TYPE_CODE,
				ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.getVersion(config),
				config.hasParameter(PARAM_UPDATED_BY) ? config.getString(PARAM_UPDATED_BY) : null
		);
	}
}
