package ch.atexxi.chronivaro.core.model;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;

import java.time.LocalDate;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class AbsenceHelper {

	public record DayAbsenceBreakdown(int paidMinutes, int unpaidMinutes, int vacationMinutes) {
		public int totalCreditedMinutes() {
			return paidMinutes + vacationMinutes;
		}

		public int totalAllMinutes() {
			return paidMinutes + unpaidMinutes + vacationMinutes;
		}
	}

	public static DayAbsenceBreakdown getDayAbsenceBreakdown(StrolchTransaction tx, String employeeId, LocalDate date) {
		int paid = 0;
		int unpaid = 0;
		int vacation = 0;

		java.util.List<Resource> absences = tx
				.streamResources(TYPE_ABSENCE)
				.filter(a -> a.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(a -> a.getString(PARAM_STATE).equals(STATE_APPROVED))
				.filter(a -> {
					LocalDate start = a.getDate(PARAM_START).toLocalDate();
					LocalDate end = a.getDate(PARAM_END).toLocalDate();
					return !date.isBefore(start) && !date.isAfter(end);
				})
				.toList();

		for (Resource a : absences) {
			int min = calculateMinutesForDay(tx, employeeId, a, date);
			if (min <= 0)
				continue;
			Resource absenceType = tx.getResourceByRelation(a, PARAM_ABSENCE_TYPE, false);
			if (absenceType != null && absenceType.hasParameter(PARAM_REDUCE_VACATION_CREDIT)
					&& absenceType.getBoolean(PARAM_REDUCE_VACATION_CREDIT)) {
				vacation += min;
			} else if (absenceType != null && ((absenceType.hasParameter(PARAM_PAID) && absenceType.getBoolean(PARAM_PAID))
					|| (absenceType.hasParameter(PARAM_COUNT_AS_TARGET_TIME)
							&& absenceType.getBoolean(PARAM_COUNT_AS_TARGET_TIME)))) {
				paid += min;
			} else if (absenceType != null && (!absenceType.hasParameter(PARAM_PAID) || !absenceType.getBoolean(PARAM_PAID))
					&& (!absenceType.hasParameter(PARAM_COUNT_AS_TARGET_TIME)
							|| !absenceType.getBoolean(PARAM_COUNT_AS_TARGET_TIME))) {
				unpaid += min;
			} else {
				paid += min;
			}
		}

		return new DayAbsenceBreakdown(paid, unpaid, vacation);
	}

	public static int getAbsenceMinutes(StrolchTransaction tx, String employeeId, LocalDate date) {
		return getDayAbsenceBreakdown(tx, employeeId, date).totalCreditedMinutes();
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
				.filter(t -> t.getString(PARAM_CODE).equalsIgnoreCase(absenceTypeCode))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Absence type not found: " + absenceTypeCode));
	}

	public static void validateCommentRequired(Resource absenceType, String comment) {
		if (absenceType.hasParameter(PARAM_COMMENT_REQUIRED) && absenceType.getBoolean(PARAM_COMMENT_REQUIRED)) {
			if (comment == null || comment.trim().isEmpty()) {
				throw new IllegalArgumentException(
						"Comment is required for absence type " + absenceType.getString(PARAM_CODE));
			}
		}
	}

	public static void validateDurationType(Resource absenceType, String durationType) {
		if (absenceType.hasParameter(PARAM_DURATION_TYPES) && durationType != null) {
			java.util.List<String> allowed = absenceType.getStringList(PARAM_DURATION_TYPES);
			if (allowed != null && !allowed.isEmpty()) {
				boolean match = allowed.stream().anyMatch(a -> a.equalsIgnoreCase(durationType));
				if (!match) {
					throw new IllegalArgumentException("Duration type " + durationType
							+ " is not allowed for absence type " + absenceType.getString(PARAM_CODE));
				}
			}
		}
	}

	public static void validateNoOverlap(StrolchTransaction tx, String employeeId, java.time.ZonedDateTime start,
			java.time.ZonedDateTime end, String excludeId) {
		LocalDate startDate = start.toLocalDate();
		LocalDate endDate = end.toLocalDate();

		java.util.List<Resource> overlapping = tx
				.streamResources(TYPE_ABSENCE)
				.filter(a -> a.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(a -> excludeId == null || !a.getId().equals(excludeId))
				.filter(a -> {
					String state = a.getString(PARAM_STATE);
					return state.equals(STATE_SUBMITTED) || state.equals(STATE_APPROVED);
				})
				.filter(a -> {
					LocalDate aStart = a.getDate(PARAM_START).toLocalDate();
					LocalDate aEnd = a.getDate(PARAM_END).toLocalDate();
					return !startDate.isAfter(aEnd) && !endDate.isBefore(aStart);
				})
				.toList();

		if (!overlapping.isEmpty()) {
			throw new IllegalArgumentException(
					"Absence overlaps with an existing active absence: " + overlapping.getFirst().getId());
		}
	}
}
