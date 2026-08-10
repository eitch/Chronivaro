package ch.atexxi.chronivaro.core.model;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;

import java.time.LocalDate;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class AbsenceHelper {

	public static int getAbsenceMinutes(StrolchTransaction tx, String employeeId, LocalDate date) {
		return tx
				.streamResources(TYPE_ABSENCE)
				.filter(a -> a.getString(BAG_RELATIONS, TYPE_EMPLOYEE).equals(employeeId))
				.filter(a -> a.getString(PARAM_STATE).equals(STATE_APPROVED))
				.filter(a -> {
					LocalDate start = a.getDate(PARAM_START).toLocalDate();
					LocalDate end = a.getDate(PARAM_END).toLocalDate();
					return !date.isBefore(start) && !date.isAfter(end);
				})
				.mapToInt(a -> calculateMinutesForDay(tx, employeeId, a, date))
				.sum();
	}

	private static int calculateMinutesForDay(StrolchTransaction tx, String employeeId, Resource absence,
			LocalDate date) {
		String durationType = absence.getString(PARAM_DURATION_TYPE);
		int targetMinutes = ScheduleHelper.getTargetMinutes(tx, employeeId, date);

		if (targetMinutes == 0)
			return 0;

		return switch (durationType) {
			case DURATION_FULL_DAY -> targetMinutes;
			case DURATION_HALF_DAY -> (int) Math.round(targetMinutes / 2.0);
			case DURATION_HOURS -> absence.getInteger(PARAM_MINUTES);
			default -> 0;
		};
	}

	public static Resource getAbsenceType(StrolchTransaction tx, String absenceTypeCode) {
		return tx
				.streamResources(TYPE_ABSENCE_TYPE)
				.filter(t -> t.getString(PARAM_CODE).equals(absenceTypeCode))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Absence type not found: " + absenceTypeCode));
	}
}
