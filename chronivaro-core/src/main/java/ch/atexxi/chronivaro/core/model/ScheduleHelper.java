package ch.atexxi.chronivaro.core.model;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class ScheduleHelper {

	public static Optional<Resource> findScheduleVersion(StrolchTransaction tx, String employeeId, LocalDate date) {
		return tx
				.streamResources(TYPE_EMPLOYMENT_SCHEDULE_VERSION)
				.filter(v -> v.getString(BAG_RELATIONS, PARAM_EMPLOYEE).equals(employeeId))
				.filter(v -> {
					LocalDate validFrom = v.getDate(PARAM_VALID_FROM).toLocalDate();
					if (date.isBefore(validFrom))
						return false;
					if (!v.hasParameter(PARAM_VALID_TO) || v.getDate(PARAM_VALID_TO) == null)
						return true;
					LocalDate validTo = v.getDate(PARAM_VALID_TO).toLocalDate();
					return !date.isAfter(validTo);
				})
				.max(Comparator.comparing(v -> v.getDate(PARAM_VALID_FROM)));
	}

	public static int getTargetMinutes(StrolchTransaction tx, String employeeId, LocalDate date) {
		Resource employee = ChronivaroModelHelper.getEmployee(tx, employeeId);
		if (!ChronivaroModelHelper.isEmployeeActive(employee, date))
			return 0;

		Optional<Resource> version = findScheduleVersion(tx, employeeId, date);
		if (version.isEmpty())
			return 0;

		Resource v = version.get();
		DayOfWeek dayOfWeek = date.getDayOfWeek();
		String paramName = PARAM_DAILY_TARGET_MINUTES + dayOfWeek.name().charAt(0) + dayOfWeek
				.name()
				.substring(1)
				.toLowerCase();
		// In Strolch we can use dynamic parameter names if we define them correctly
		// But for now let's assume parameters are named like dailyTargetMinutesMonday, etc.
		// Or we use a bag for daily values
		if (v.hasParameter(BAG_PARAMETERS, paramName)) {
			return v.getInteger(paramName);
		}

		return 0;
	}
}
