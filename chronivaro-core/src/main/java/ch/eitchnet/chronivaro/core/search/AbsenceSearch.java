package ch.eitchnet.chronivaro.core.search;

import li.strolch.search.ResourceSearch;

import java.util.Collection;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.utils.helper.StringHelper.isNotEmpty;

public class AbsenceSearch extends ResourceSearch {

	public AbsenceSearch() {
		types(TYPE_ABSENCE);
	}

	public AbsenceSearch forEmployee(String employeeId) {
		if (isNotEmpty(employeeId))
			where(param(BAG_RELATIONS, PARAM_EMPLOYEE, isEqualTo(employeeId)));
		return this;
	}

	public AbsenceSearch forEmployees(Collection<String> employeeIds) {
		if (employeeIds != null && !employeeIds.isEmpty())
			where(param(BAG_RELATIONS, PARAM_EMPLOYEE, isIn(employeeIds)));
		return this;
	}

	public AbsenceSearch forState(String state) {
		if (isNotEmpty(state))
			where(param(BAG_PARAMETERS, PARAM_STATE, isEqualTo(state)));
		return this;
	}

	public AbsenceSearch forAbsenceType(String absenceTypeId) {
		if (isNotEmpty(absenceTypeId))
			where(param(BAG_RELATIONS, PARAM_ABSENCE_TYPE, isEqualTo(absenceTypeId)));
		return this;
	}
}
