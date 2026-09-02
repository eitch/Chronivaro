package ch.eitchnet.chronivaro.core.search;

import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.search.ResourceSearch;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

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
					if (!isHrOrAdmin) {
						String userCertId = tx.getCertificate().getUserId();
						boolean isSelf = ChronivaroModelHelper.findEmployeeByUser(tx, userCertId)
								.map(e -> e.getId().equals(empId))
								.orElse(false);
						if (!isSelf && !supervised.contains(empId)) {
							return false;
						}
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
