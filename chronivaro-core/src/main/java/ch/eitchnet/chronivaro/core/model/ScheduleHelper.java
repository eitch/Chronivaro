package ch.eitchnet.chronivaro.core.model;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;

public class ScheduleHelper {

	public static Optional<Resource> findScheduleVersion(StrolchTransaction tx, String employeeId) {
		Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
		if (!employee.hasRelation(PARAM_CURRENT_SCHEDULE))
			return Optional.empty();
		return Optional.ofNullable(tx.getResourceByRelation(employee, PARAM_CURRENT_SCHEDULE, false));
	}

	public static Optional<Resource> findScheduleVersion(StrolchTransaction tx, String employeeId, LocalDate date) {
		Optional<Resource> version = tx.streamResources(TYPE_EMPLOYMENT_SCHEDULE)
				.filter(r -> r.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(r -> {
					LocalDate from = r.getDate(PARAM_VALID_FROM).toLocalDate();
					if (date.isBefore(from))
						return false;

					if (r.hasParameter(PARAM_VALID_TO)) {
						LocalDate to = r.getDate(PARAM_VALID_TO).toLocalDate();
						return !date.isAfter(to);
					}

					return true;
				})
				.findFirst();

		if (version.isEmpty()) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			if (employee.hasRelation(PARAM_CURRENT_SCHEDULE)) {
				Resource current = tx.getResourceByRelation(employee, PARAM_CURRENT_SCHEDULE, false);
				if (current != null) {
					LocalDate from = current.getDate(PARAM_VALID_FROM).toLocalDate();
					if (!date.isBefore(from)) {
						if (!current.hasParameter(PARAM_VALID_TO) || !date.isAfter(current.getDate(PARAM_VALID_TO).toLocalDate())) {
							return Optional.of(current);
						}
					}
				}
			}
		}

		return version;
	}

	public static int getTargetMinutes(StrolchTransaction tx, String employeeId, LocalDate date) {
		Resource employee = ChronivaroModelHelper.getEmployee(tx, employeeId);
		if (!ChronivaroModelHelper.isEmployeeActive(employee, date))
			return 0;

		Optional<Resource> version = findScheduleVersion(tx, employeeId, date);

		if (version.isEmpty())
			return 0;

		Resource scheduleVersion = version.get();
		DayOfWeek dayOfWeek = date.getDayOfWeek();
		String paramName = PARAM_DAILY_TARGET_MINUTES + dayOfWeek.name().charAt(0) + dayOfWeek
				.name()
				.substring(1)
				.toLowerCase();

		// In Strolch we can use dynamic parameter names if we define them correctly
		// But for now let's assume parameters are named like dailyTargetMinutesMonday, etc.
		// Or we use a bag for daily values
		if (scheduleVersion.hasParameter(paramName)) {
			int targetMinutes = scheduleVersion.getInteger(paramName);
			if (targetMinutes > 0)
				return targetMinutes;
		}

		// Fallback to general dailyTargetMinutes
		if (scheduleVersion.hasParameter(PARAM_DAILY_TARGET_MINUTES)) {
			return scheduleVersion.getInteger(PARAM_DAILY_TARGET_MINUTES);
		}

		return 0;
	}
}
