package ch.atexxi.chronivaro.core.model;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class ScheduleHelper {

	public static Optional<Resource> findScheduleVersion(StrolchTransaction tx, String employeeId) {
		Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
		return Optional.ofNullable(tx.getResourceByRelation(employee, PARAM_CURRENT_SCHEDULE, true));
	}

	public static int getTargetMinutes(StrolchTransaction tx, String employeeId, LocalDate date) {
		Resource employee = ChronivaroModelHelper.getEmployee(tx, employeeId);
		if (!ChronivaroModelHelper.isEmployeeActive(employee, date))
			return 0;

		Optional<Resource> version;
		if (date.equals(LocalDate.now()) && employee.hasRelation(PARAM_CURRENT_SCHEDULE)) {
			version = Optional.of(tx.getResourceByRelation(employee, PARAM_CURRENT_SCHEDULE, true));
		} else {
			version = findScheduleVersion(tx, employeeId);
		}

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
