package ch.eitchnet.chronivaro.core.model;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.base.AccessDeniedException;
import li.strolch.privilege.model.Certificate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;

public class ChronivaroModelHelper {

	public static Resource getEmployee(StrolchTransaction tx, String employeeId) {
		return tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
	}

	public static Resource getTeam(StrolchTransaction tx, String teamId) {
		return tx.getResourceBy(TYPE_TEAM, teamId, true);
	}

	public static Resource getLocation(StrolchTransaction tx, String locationId) {
		return tx.getResourceBy(TYPE_LOCATION, locationId, true);
	}

	public static Resource getAbsenceType(StrolchTransaction tx, String absenceTypeId) {
		return tx.getResourceBy(TYPE_ABSENCE_TYPE, absenceTypeId, true);
	}

	public static Optional<Resource> findEmployeeByUser(StrolchTransaction tx, String userId) {
		if (userId == null)
			return Optional.empty();
		return tx
				.streamResources(TYPE_EMPLOYEE)
				.filter(e -> (e.hasParameter(PARAM_USER_ID) && userId.equals(e.getString(PARAM_USER_ID)))
						|| (e.hasParameter(PARAM_USERNAME) && userId.equals(e.getString(PARAM_USERNAME)))
						|| (tx.getCertificate() != null && tx.getCertificate().getUsername() != null && (
								(e.hasParameter(PARAM_USER_ID) && tx.getCertificate().getUsername().equals(e.getString(PARAM_USER_ID)))
								|| (e.hasParameter(PARAM_USERNAME) && tx.getCertificate().getUsername().equals(e.getString(PARAM_USERNAME)))
						)))
				.findFirst();
	}

	public static List<Resource> findEmployeesByTeam(StrolchTransaction tx, String teamId) {
		return tx
				.streamResources(TYPE_EMPLOYEE)
				.filter(e -> e.hasRelation(PARAM_PRIMARY_TEAM) && teamId.equals(e.getRelationId(PARAM_PRIMARY_TEAM)))
				.toList();
	}

	public static List<String> getSupervisedEmployeeIds(StrolchTransaction tx, Certificate cert) {
		if (tx.getPrivilegeContext().hasRole(ROLE_HR)
				|| tx.getPrivilegeContext().hasRole(ROLE_ADMIN)
				|| tx.getPrivilegeContext().hasRole(ROLE_ADMINISTRATOR)
				|| tx.getPrivilegeContext().hasRole(ROLE_STROLCH_ADMIN)
				|| tx.getPrivilegeContext().hasRole(ROLE_PRIVILEGE_ADMIN)) {
			return tx.streamResources(TYPE_EMPLOYEE).map(Resource::getId).toList();
		}

		if (tx.getPrivilegeContext().hasRole(ROLE_SUPERVISOR)) {
			Set<String> supervisedTeamIds = new HashSet<>();
			Optional<Resource> supervisorEmp = findEmployeeByUser(tx, cert.getUserId());
			if (supervisorEmp.isPresent()) {
				Resource supervisor = supervisorEmp.get();
				String supervisorId = supervisor.getId();
				if (supervisor.hasRelation(PARAM_PRIMARY_TEAM)) {
					supervisedTeamIds.add(supervisor.getRelationId(PARAM_PRIMARY_TEAM));
				}
				tx.streamResources(TYPE_TEAM).forEach(t -> {
					if (t.hasRelation(PARAM_LEADER) && supervisorId.equals(t.getRelationId(PARAM_LEADER))) {
						supervisedTeamIds.add(t.getId());
					} else if (t.hasParameter(PARAM_LEADER) && (supervisorId.equals(t.getString(PARAM_LEADER))
							|| cert.getUsername().equals(t.getString(PARAM_LEADER)))) {
						supervisedTeamIds.add(t.getId());
					}
				});
			} else {
				tx.streamResources(TYPE_TEAM).forEach(t -> {
					if ((t.hasParameter(PARAM_LEADER) && cert.getUsername().equals(t.getString(PARAM_LEADER)))
							|| (t.hasRelation(PARAM_LEADER) && cert.getUsername().equals(t.getRelationId(PARAM_LEADER)))) {
						supervisedTeamIds.add(t.getId());
					}
				});
			}

			if (supervisedTeamIds.isEmpty())
				return Collections.emptyList();

			return tx.streamResources(TYPE_EMPLOYEE)
					.filter(e -> e.hasRelation(PARAM_PRIMARY_TEAM) && supervisedTeamIds.contains(e.getRelationId(PARAM_PRIMARY_TEAM)))
					.map(Resource::getId)
					.toList();
		}

		return Collections.emptyList();
	}

	public static void assertCanManageEmployee(StrolchTransaction tx, String targetEmployeeId) {
		if (tx.getPrivilegeContext().hasRole(ROLE_HR)
				|| tx.getPrivilegeContext().hasRole(ROLE_ADMIN)
				|| tx.getPrivilegeContext().hasRole(ROLE_ADMINISTRATOR)
				|| tx.getPrivilegeContext().hasRole(ROLE_STROLCH_ADMIN)
				|| tx.getPrivilegeContext().hasRole(ROLE_PRIVILEGE_ADMIN)) {
			return;
		}

		if (tx.getPrivilegeContext().hasRole(ROLE_SUPERVISOR)) {
			List<String> supervised = getSupervisedEmployeeIds(tx, tx.getCertificate());
			if (!supervised.contains(targetEmployeeId)) {
				throw new AccessDeniedException("Access denied: Employee " + targetEmployeeId
						+ " is not in your supervised team(s).");
			}

			// Self-approval restriction
			Optional<Resource> currentEmp = findEmployeeByUser(tx, tx.getCertificate().getUserId());
			if (currentEmp.isPresent() && currentEmp.get().getId().equals(targetEmployeeId)) {
				throw new AccessDeniedException(
						"Access denied: Supervisors cannot approve or reject their own requests.");
			}
			return;
		}

		throw new AccessDeniedException("Access denied: You do not have supervisor or administrative privileges.");
	}

	public static ZoneId getEmployeeTimezone(Resource employee) {
		if (!employee.hasParameter(PARAM_TIMEZONE))
			return ZoneId.of("Europe/Zurich");
		String tz = employee.getString(PARAM_TIMEZONE);
		return tz == null || tz.isEmpty() ? ZoneId.of("Europe/Zurich") : ZoneId.of(tz);
	}

	public static LocalDate getJoinDate(Resource employee) {
		if (!employee.hasParameter(PARAM_JOIN_DATE))
			return LocalDate.of(1970, 1, 1);
		return employee.getDate(PARAM_JOIN_DATE).toLocalDate();
	}

	public static Optional<LocalDate> getExitDate(Resource employee) {
		if (!employee.hasParameter(PARAM_EXIT_DATE))
			return Optional.empty();
		ZonedDateTime exitDate = employee.getDate(PARAM_EXIT_DATE);
		if (exitDate.getYear() == 9999)
			return Optional.empty();
		return Optional.of(exitDate.toLocalDate());
	}

	public static boolean isEmployeeActive(Resource employee, LocalDate date) {
		if (employee.hasParameter(PARAM_ACTIVE) && !employee.getBoolean(PARAM_ACTIVE))
			return false;

		LocalDate joinDate = getJoinDate(employee);
		if (date.isBefore(joinDate))
			return false;

		Optional<LocalDate> exitDate = getExitDate(employee);
		return exitDate.map(localDate -> !date.isAfter(localDate)).orElse(true);
	}
}
