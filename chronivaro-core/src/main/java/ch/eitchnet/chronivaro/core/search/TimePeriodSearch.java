package ch.eitchnet.chronivaro.core.search;

import li.strolch.search.ResourceSearch;

import java.time.YearMonth;
import java.util.Collection;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.search.PredicatesSupport.startsWith;
import static li.strolch.utils.helper.StringHelper.isNotEmpty;

public class TimePeriodSearch extends ResourceSearch {

	public TimePeriodSearch() {
		types(TYPE_TIME_PERIOD);
	}

	public TimePeriodSearch forEmployee(String employeeId) {
		if (isNotEmpty(employeeId))
			where(param(BAG_RELATIONS, PARAM_EMPLOYEE, isEqualTo(employeeId)));
		return this;
	}

	public TimePeriodSearch forEmployees(Collection<String> employeeIds) {
		if (employeeIds != null && !employeeIds.isEmpty())
			where(param(BAG_RELATIONS, PARAM_EMPLOYEE, isIn(employeeIds)));
		return this;
	}

	public TimePeriodSearch forYearMonth(YearMonth yearMonth) {
		if (yearMonth != null)
			where(param(BAG_PARAMETERS, PARAM_YEAR_MONTH, isEqualTo(yearMonth.toString())));
		return this;
	}

	public TimePeriodSearch forYearMonth(String yearMonthStr) {
		if (isNotEmpty(yearMonthStr))
			where(param(BAG_PARAMETERS, PARAM_YEAR_MONTH, isEqualTo(yearMonthStr)));
		return this;
	}

	public TimePeriodSearch forState(String state) {
		if (isNotEmpty(state))
			where(param(BAG_PARAMETERS, PARAM_STATE, isEqualTo(state)));
		return this;
	}

	public TimePeriodSearch forYear(int year) {
		where(param(BAG_PARAMETERS, PARAM_YEAR_MONTH, startsWith(year + "-")));
		return this;
	}
}
