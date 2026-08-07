package ch.atexxi.chronivaro.core.model;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

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
		return tx.streamResources(TYPE_EMPLOYEE)
				.filter(e -> e.getString(BAG_RELATIONS, PARAM_USER).equals(userId))
				.findFirst();
	}

	public static ZoneId getEmployeeTimezone(Resource employee) {
		String tz = employee.getString(PARAM_TIMEZONE);
		return tz == null || tz.isEmpty() ? ZoneId.of("Europe/Zurich") : ZoneId.of(tz);
	}

	public static LocalDate getJoinDate(Resource employee) {
		return employee.getDate(PARAM_JOIN_DATE).toLocalDate();
	}

	public static Optional<LocalDate> getExitDate(Resource employee) {
		if (!employee.hasParameter(PARAM_EXIT_DATE) || employee.getDate(PARAM_EXIT_DATE) == null)
			return Optional.empty();
		return Optional.of(employee.getDate(PARAM_EXIT_DATE).toLocalDate());
	}

	public static boolean isEmployeeActive(Resource employee, LocalDate date) {
		if (!employee.getBoolean(PARAM_ACTIVE))
			return false;

		LocalDate joinDate = getJoinDate(employee);
		if (date.isBefore(joinDate))
			return false;

		Optional<LocalDate> exitDate = getExitDate(employee);
		return exitDate.map(localDate -> !date.isAfter(localDate)).orElse(true);
	}
}
