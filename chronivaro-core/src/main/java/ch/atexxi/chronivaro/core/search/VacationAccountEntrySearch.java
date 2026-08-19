package ch.atexxi.chronivaro.core.search;

import li.strolch.search.ResourceSearch;
import li.strolch.utils.collections.DateRange;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.search.PredicatesSupport.isAfter;
import static li.strolch.search.PredicatesSupport.isBefore;
import static li.strolch.utils.helper.StringHelper.isNotEmpty;

public class VacationAccountEntrySearch extends ResourceSearch {

	public VacationAccountEntrySearch() {
		types(TYPE_VACATION_ACCOUNT_ENTRY);
	}

	public VacationAccountEntrySearch forEmployee(String employeeId) {
		if (isNotEmpty(employeeId))
			where(param(BAG_RELATIONS, PARAM_EMPLOYEE, isEqualTo(employeeId)));
		return this;
	}

	public VacationAccountEntrySearch forVacationType(String vacationType) {
		if (isNotEmpty(vacationType))
			where(param(BAG_PARAMETERS, PARAM_VACATION_TYPE, isEqualTo(vacationType)));
		return this;
	}

	public VacationAccountEntrySearch forAbsence(String absenceId) {
		if (isNotEmpty(absenceId))
			where(param(BAG_RELATIONS, PARAM_ABSENCE, isEqualTo(absenceId)));
		return this;
	}

	public VacationAccountEntrySearch forYear(int year, ZoneId zone) {
		ZonedDateTime start = LocalDate.of(year, 1, 1).atStartOfDay(zone);
		ZonedDateTime end = LocalDate.of(year, 12, 31).atTime(23, 59, 59, 999_999_999).atZone(zone);
		return inDateRange(start, end);
	}

	public VacationAccountEntrySearch inDateRange(ZonedDateTime from, ZonedDateTime to) {
		if (from != null && to != null) {
			DateRange range = new DateRange().from(from, true).to(to, true);
			where(param(BAG_PARAMETERS, PARAM_DATE, inRange(range)));
		} else if (from != null) {
			where(param(BAG_PARAMETERS, PARAM_DATE, isAfter(from, true)));
		} else if (to != null) {
			where(param(BAG_PARAMETERS, PARAM_DATE, isBefore(to, true)));
		}
		return this;
	}
}
