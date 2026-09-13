package ch.eitchnet.chronivaro.core.search;

import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.search.ResourceSearch;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;

public class OnCallPeriodSearch extends ResourceSearch {

	private String employeeId;
	private List<String> employeeIds;
	private LocalDate from;
	private LocalDate to;

	public OnCallPeriodSearch() {
		types(TYPE_ON_CALL_PERIOD);
	}

	public OnCallPeriodSearch forEmployee(String employeeId) {
		this.employeeId = employeeId;
		return this;
	}

	public OnCallPeriodSearch forEmployees(List<String> employeeIds) {
		this.employeeIds = employeeIds;
		return this;
	}

	public OnCallPeriodSearch between(LocalDate from, LocalDate to) {
		this.from = from;
		this.to = to;
		return this;
	}

	public List<Resource> searchPeriods(StrolchTransaction tx) {
		List<String> supervised = ChronivaroModelHelper.getSupervisedEmployeeIds(tx, tx.getCertificate());
		boolean isHrOrAdmin = tx.getPrivilegeContext().hasRole(ROLE_HR)
				|| tx.getPrivilegeContext().hasRole(ROLE_ADMIN)
				|| tx.getPrivilegeContext().hasRole(ROLE_ADMINISTRATOR)
				|| tx.getPrivilegeContext().hasRole(ROLE_STROLCH_ADMIN)
				|| tx.getPrivilegeContext().hasRole(ROLE_PRIVILEGE_ADMIN);

		final Set<String> allowedEmployeeIds;
		if (isHrOrAdmin) {
			allowedEmployeeIds = null;
		} else {
			allowedEmployeeIds = new HashSet<>();
			if (tx.getPrivilegeContext().hasRole(ROLE_SUPERVISOR)) {
				allowedEmployeeIds.addAll(supervised);
			}
			Optional<Resource> callerEmp = ChronivaroModelHelper.findEmployeeByUser(tx, tx.getCertificate().getUserId());
			if (callerEmp.isPresent()) {
				Resource emp = callerEmp.get();
				allowedEmployeeIds.add(emp.getId());
				if (emp.hasRelation(PARAM_PRIMARY_TEAM)) {
					String teamId = emp.getRelationId(PARAM_PRIMARY_TEAM);
					if (teamId != null && !teamId.isBlank()) {
						for (Resource e : ChronivaroModelHelper.findEmployeesByTeam(tx, teamId)) {
							allowedEmployeeIds.add(e.getId());
						}
					}
				}
			}
		}

		return search(tx).toList().stream()
				.filter(period -> {
					String empId = period.getRelationId(PARAM_EMPLOYEE);
					if (this.employeeId != null && !this.employeeId.equals(empId)) {
						return false;
					}
					if (this.employeeIds != null && !this.employeeIds.contains(empId)) {
						return false;
					}

					// Privilege filter
					if (allowedEmployeeIds != null && !allowedEmployeeIds.contains(empId)) {
						return false;
					}

					if (from != null || to != null) {
						LocalDate start = period.getDate(PARAM_START_DATE).toLocalDate();
						LocalDate end = period.getDate(PARAM_END_DATE).toLocalDate();
						if (from != null && end.isBefore(from)) {
							return false;
						}
						if (to != null && start.isAfter(to)) {
							return false;
						}
					}

					return true;
				})
				.sorted((p1, p2) -> {
					ZonedDateTime d1 = p1.getDate(PARAM_START_DATE);
					ZonedDateTime d2 = p2.getDate(PARAM_START_DATE);
					return d1.compareTo(d2);
				})
				.toList();
	}
}
