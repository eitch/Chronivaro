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
}
