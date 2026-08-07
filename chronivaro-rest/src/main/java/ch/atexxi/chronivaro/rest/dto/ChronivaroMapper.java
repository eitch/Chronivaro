package ch.atexxi.chronivaro.rest.dto;

import ch.atexxi.chronivaro.core.model.DaySummary;
import ch.atexxi.chronivaro.core.model.MonthSummary;
import li.strolch.model.Resource;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class ChronivaroMapper {

	public static WorkEntryDto toDto(Resource workEntry) {
		return new WorkEntryDto(
				workEntry.getId(),
				workEntry.getString(BAG_RELATIONS, TYPE_EMPLOYEE),
				workEntry.getDate(PARAM_START),
				workEntry.getDate(PARAM_END),
				workEntry.getString(PARAM_SOURCE),
				workEntry.getString(PARAM_COMMENT),
				workEntry.getString(PARAM_CREATED_BY)
		);
	}

	public static AbsenceDto toDto(Resource absence, String absenceTypeCode) {
		return new AbsenceDto(
				absence.getId(),
				absence.getString(BAG_RELATIONS, TYPE_EMPLOYEE),
				absenceTypeCode,
				absence.getDate(PARAM_START),
				absence.getDate(PARAM_END),
				absence.getString(PARAM_DURATION_TYPE),
				absence.hasParameter(PARAM_DAY_PART) ? absence.getString(PARAM_DAY_PART) : null,
				absence.hasParameter(PARAM_MINUTES) ? absence.getInteger(PARAM_MINUTES) : null,
				absence.getString(PARAM_COMMENT),
				absence.getString(PARAM_STATE)
		);
	}

	public static DaySummaryDto toDto(DaySummary summary) {
		return new DaySummaryDto(
				summary.date(),
				summary.targetMinutes(),
				summary.actualMinutes(),
				summary.holidayMinutes(),
				summary.absenceMinutes(),
				summary.getBalance(),
				summary.workEntries().stream().map(e -> new WorkEntryRangeDto(e.id(), e.start(), e.end(), e.durationMinutes())).toList(),
				summary.breaks().stream().map(b -> new BreakRangeDto(b.start(), b.end(), b.durationMinutes())).toList()
		);
	}

	public static MonthSummaryDto toDto(MonthSummary summary) {
		return new MonthSummaryDto(
				summary.employeeId(),
				summary.yearMonth(),
				summary.totalTargetMinutes(),
				summary.totalActualMinutes(),
				summary.totalHolidayMinutes(),
				summary.totalAbsenceMinutes(),
				summary.initialBalanceMinutes(),
				summary.getPeriodBalance(),
				summary.getEndBalance(),
				summary.daySummaries().stream().map(ChronivaroMapper::toDto).toList()
		);
	}

	public static TeamDto teamToDto(Resource team) {
		return new TeamDto(team.getId(), team.getString(PARAM_NAME));
	}

	public static LocationDto locationToDto(Resource location) {
		return new LocationDto(
				location.getId(),
				location.getString(PARAM_NAME),
				location.getString(PARAM_TIMEZONE),
				location.getString(BAG_RELATIONS, TYPE_HOLIDAY_CALENDAR)
		);
	}

	public static AbsenceTypeDto absenceTypeToDto(Resource type) {
		return new AbsenceTypeDto(
				type.getId(),
				type.getString(PARAM_CODE),
				type.getString(PARAM_NAME),
				type.getBoolean(PARAM_COUNT_AS_TARGET_TIME),
				type.getBoolean(PARAM_REDUCE_VACATION_CREDIT),
				type.getBoolean(PARAM_PAID),
				type.getBoolean(PARAM_APPROVAL_REQUIRED),
				type.getStringList(PARAM_DURATION_TYPES),
				type.getBoolean(PARAM_ACTIVE)
		);
	}

	public static EmployeeDto employeeToDto(Resource employee) {
		return new EmployeeDto(
				employee.getId(),
				employee.getString(PARAM_PERSONAL_NUMBER),
				employee.getString(PARAM_DISPLAY_NAME),
				employee.getString(BAG_RELATIONS, TYPE_TEAM),
				employee.getString(BAG_RELATIONS, TYPE_LOCATION),
				employee.getString(PARAM_TIMEZONE),
				employee.getDate(PARAM_JOIN_DATE).toLocalDate(),
				employee.hasParameter(PARAM_EXIT_DATE) && employee.getDate(PARAM_EXIT_DATE) != null ?
						employee.getDate(PARAM_EXIT_DATE).toLocalDate() : null,
				employee.getBoolean(PARAM_ACTIVE),
				employee.getString(BAG_RELATIONS, PARAM_USER)
		);
	}
}
