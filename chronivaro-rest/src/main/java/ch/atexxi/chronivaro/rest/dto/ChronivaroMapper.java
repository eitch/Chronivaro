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
				info.isOff());
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
				summary.getBalance(), summary
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
}
