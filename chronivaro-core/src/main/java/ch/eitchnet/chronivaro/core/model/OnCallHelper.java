package ch.eitchnet.chronivaro.core.model;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;

public class OnCallHelper {

	public static List<Resource> findOnCallPeriods(StrolchTransaction tx, String employeeId) {
		return tx.streamResources(TYPE_ON_CALL_PERIOD)
				.filter(p -> employeeId.equals(p.getRelationId(PARAM_EMPLOYEE)))
				.toList();
	}

	public static Optional<Resource> findActiveOnCallPeriod(StrolchTransaction tx, String employeeId, ZonedDateTime time) {
		List<Resource> periods = findOnCallPeriods(tx, employeeId);
		for (Resource period : periods) {
			if (isWithinPeriod(period, time)) {
				return Optional.of(period);
			}
		}
		return Optional.empty();
	}

	public static boolean hasActiveOnCallPeriod(StrolchTransaction tx, String employeeId, ZonedDateTime time) {
		return findActiveOnCallPeriod(tx, employeeId, time).isPresent();
	}

	public static boolean isWithinPeriod(Resource onCallPeriod, ZonedDateTime time) {
		ZoneId tz = time.getZone();
		LocalDate targetDate = time.toLocalDate();
		LocalTime targetTime = time.toLocalTime();

		LocalDate startDate = onCallPeriod.getDate(PARAM_START_DATE).withZoneSameInstant(tz).toLocalDate();
		LocalDate endDate = onCallPeriod.getDate(PARAM_END_DATE).withZoneSameInstant(tz).toLocalDate();

		if (targetDate.isBefore(startDate) || targetDate.isAfter(endDate)) {
			return false;
		}

		String startTimeStr = onCallPeriod.hasParameter(PARAM_START_TIME) ? onCallPeriod.getString(PARAM_START_TIME) : "";
		String endTimeStr = onCallPeriod.hasParameter(PARAM_END_TIME) ? onCallPeriod.getString(PARAM_END_TIME) : "";

		LocalTime startTime = (startTimeStr != null && !startTimeStr.isBlank()) ? LocalTime.parse(startTimeStr.trim()) : LocalTime.MIN;
		LocalTime endTime = (endTimeStr != null && !endTimeStr.isBlank()) ? LocalTime.parse(endTimeStr.trim()) : LocalTime.MAX;

		if (targetDate.isEqual(startDate) && targetTime.isBefore(startTime)) {
			return false;
		}
		if (targetDate.isEqual(endDate) && targetTime.isAfter(endTime)) {
			return false;
		}

		return true;
	}

	public static boolean isOffDutyHours(StrolchTransaction tx, ZonedDateTime time) {
		LocalTime t = time.toLocalTime();
		LocalTime officeStart = LocalTime.parse(DEFAULT_OFFICE_HOURS_START);
		LocalTime officeEnd = LocalTime.parse(DEFAULT_OFFICE_HOURS_END);

		Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", false);
		if (config != null) {
			if (config.hasParameter(PARAM_OFFICE_HOURS_START)) {
				String s = config.getString(PARAM_OFFICE_HOURS_START);
				if (s != null && !s.isBlank()) {
					officeStart = LocalTime.parse(s.trim());
				}
			}
			if (config.hasParameter(PARAM_OFFICE_HOURS_END)) {
				String e = config.getString(PARAM_OFFICE_HOURS_END);
				if (e != null && !e.isBlank()) {
					officeEnd = LocalTime.parse(e.trim());
				}
			}
		}

		return t.isBefore(officeStart) || t.isAfter(officeEnd) || t.equals(officeEnd);
	}

	public static boolean isEligibleForOnCall(StrolchTransaction tx, String employeeId, ZonedDateTime start, ZonedDateTime end) {
		if (start == null)
			return false;
		ZonedDateTime effectiveEnd = (end == null || end.getYear() == 1970) ? start : end;

		boolean startInOnCall = hasActiveOnCallPeriod(tx, employeeId, start);
		boolean endInOnCall = hasActiveOnCallPeriod(tx, employeeId, effectiveEnd);

		return (startInOnCall || endInOnCall) && (isOffDutyHours(tx, start) || isOffDutyHours(tx, effectiveEnd));
	}
}
