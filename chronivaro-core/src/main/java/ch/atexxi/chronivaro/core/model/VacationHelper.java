package ch.atexxi.chronivaro.core.model;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class VacationHelper {

	public static int getAnnualVacationDays(StrolchTransaction tx) {
		Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", false);
		if (config != null && config.hasParameter(PARAM_ANNUAL_VACATION_DAYS)) {
			return config.getInteger(PARAM_ANNUAL_VACATION_DAYS);
		}
		return DEFAULT_ANNUAL_VACATION_DAYS;
	}

	public static int getMinutesPerVacationDay(StrolchTransaction tx) {
		Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", false);
		if (config != null && config.hasParameter(PARAM_MINUTES_PER_VACATION_DAY)) {
			return config.getInteger(PARAM_MINUTES_PER_VACATION_DAY);
		}
		return DEFAULT_MINUTES_PER_VACATION_DAY;
	}

	public static String getVacationAbsenceTypeCode(StrolchTransaction tx) {
		Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", false);
		if (config != null && config.hasParameter(PARAM_VACATION_ABSENCE_TYPE_CODE)) {
			return config.getString(PARAM_VACATION_ABSENCE_TYPE_CODE);
		}
		return DEFAULT_VACATION_ABSENCE_TYPE_CODE;
	}

	public static int getWeeklyTargetMinutes(StrolchTransaction tx) {
		Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", false);
		if (config != null && config.hasParameter(PARAM_WEEKLY_TARGET_MINUTES)) {
			return config.getInteger(PARAM_WEEKLY_TARGET_MINUTES);
		}
		return DEFAULT_WEEKLY_TARGET_MINUTES;
	}

	public static int calculateAnnualEntitlement(StrolchTransaction tx, String employeeId, int year) {
		Resource employee = ChronivaroModelHelper.getEmployee(tx, employeeId);
		LocalDate yearStart = LocalDate.of(year, 1, 1);
		LocalDate yearEnd = LocalDate.of(year, 12, 31);
		int daysInYear = yearStart.lengthOfYear();

		LocalDate joinDate = ChronivaroModelHelper.getJoinDate(employee);
		Optional<LocalDate> exitDateOpt = ChronivaroModelHelper.getExitDate(employee);

		LocalDate activeStart = yearStart.isBefore(joinDate) ? joinDate : yearStart;
		LocalDate activeEnd = exitDateOpt.map(exitDate -> yearEnd.isAfter(exitDate) ? exitDate : yearEnd).orElse(yearEnd);

		if (activeStart.isAfter(activeEnd) || joinDate.isAfter(yearEnd)
				|| (exitDateOpt.isPresent() && exitDateOpt.get().isBefore(yearStart))) {
			return 0;
		}

		int annualDays = getAnnualVacationDays(tx);
		int minPerDay = getMinutesPerVacationDay(tx);
		int globalWeeklyTargetMinutes = getWeeklyTargetMinutes(tx);
		double baseAnnualMinutes = (double) annualDays * minPerDay;

		double totalUnroundedMinutes = 0.0;
		for (LocalDate date = activeStart; !date.isAfter(activeEnd); date = date.plusDays(1)) {
			double employmentRate = getEmploymentRateOnDate(tx, employeeId, date, globalWeeklyTargetMinutes);
			totalUnroundedMinutes += (baseAnnualMinutes / daysInYear) * employmentRate;
		}

		return (int) Math.round(totalUnroundedMinutes);
	}

	public static double getEmploymentRateOnDate(StrolchTransaction tx, String employeeId, LocalDate date,
			int globalWeeklyTargetMinutes) {
		Optional<Resource> scheduleVersion = ScheduleHelper.findScheduleVersion(tx, employeeId, date);
		if (scheduleVersion.isPresent()) {
			Resource schedule = scheduleVersion.get();
			if (schedule.hasParameter(PARAM_EMPLOYMENT_RATE)) {
				double rate = schedule.getDouble(PARAM_EMPLOYMENT_RATE);
				if (rate > 1.0) {
					rate = rate / 100.0;
				}
				return rate;
			}

			int weeklyMinutes = 0;
			for (DayOfWeek dow : DayOfWeek.values()) {
				String paramName = PARAM_DAILY_TARGET_MINUTES + dow.name().charAt(0) + dow
						.name()
						.substring(1)
						.toLowerCase();
				if (schedule.hasParameter(paramName)) {
					weeklyMinutes += schedule.getInteger(paramName);
				}
			}
			if (weeklyMinutes > 0 && globalWeeklyTargetMinutes > 0) {
				return (double) weeklyMinutes / (double) globalWeeklyTargetMinutes;
			}
			if (schedule.hasParameter(PARAM_WEEKLY_TARGET_MINUTES) && globalWeeklyTargetMinutes > 0) {
				return (double) schedule.getInteger(PARAM_WEEKLY_TARGET_MINUTES) / (double) globalWeeklyTargetMinutes;
			}
		}

		return 1.0;
	}

	public static int getVacationBalance(StrolchTransaction tx, String employeeId) {
		return getVacationBalance(tx, employeeId, ZonedDateTime.now());
	}

	public static int getVacationBalance(StrolchTransaction tx, String employeeId, ZonedDateTime at) {
		return tx
				.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
				.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(e -> !e.getDate(PARAM_DATE).isAfter(at))
				.mapToInt(e -> e.getInteger(PARAM_VALUE))
				.sum();
	}

	public static VacationAccountSummary getVacationAccountSummary(StrolchTransaction tx, String employeeId, int year) {
		LocalDate yearStart = LocalDate.of(year, 1, 1);
		LocalDate yearEnd = LocalDate.of(year, 12, 31);

		int carryOverMinutes = tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
				.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(e -> e.getDate(PARAM_DATE).toLocalDate().isBefore(yearStart))
				.mapToInt(e -> e.getInteger(PARAM_VALUE))
				.sum();

		int entitlementMinutes = tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
				.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(e -> VACATION_ENTITLEMENT.equals(e.getString(PARAM_VACATION_TYPE)))
				.filter(e -> {
					LocalDate date = e.getDate(PARAM_DATE).toLocalDate();
					return !date.isBefore(yearStart) && !date.isAfter(yearEnd);
				})
				.mapToInt(e -> e.getInteger(PARAM_VALUE))
				.sum();

		int correctionsMinutes = tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
				.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(e -> VACATION_CORRECTION.equals(e.getString(PARAM_VACATION_TYPE)))
				.filter(e -> {
					LocalDate date = e.getDate(PARAM_DATE).toLocalDate();
					return !date.isBefore(yearStart) && !date.isAfter(yearEnd);
				})
				.mapToInt(e -> e.getInteger(PARAM_VALUE))
				.sum();

		int usageEntriesSum = tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
				.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(e -> VACATION_USAGE.equals(e.getString(PARAM_VACATION_TYPE)))
				.filter(e -> {
					LocalDate date = e.getDate(PARAM_DATE).toLocalDate();
					return !date.isBefore(yearStart) && !date.isAfter(yearEnd);
				})
				.mapToInt(e -> e.getInteger(PARAM_VALUE))
				.sum();

		int usageMinutes = Math.abs(usageEntriesSum);
		int remainingMinutes = carryOverMinutes + entitlementMinutes + correctionsMinutes - usageMinutes;

		return new VacationAccountSummary(employeeId, year, carryOverMinutes, entitlementMinutes,
				correctionsMinutes, usageMinutes, remainingMinutes);
	}

	public static Optional<Resource> findEntitlementEntry(StrolchTransaction tx, String employeeId, int year) {
		LocalDate yearStart = LocalDate.of(year, 1, 1);
		LocalDate yearEnd = LocalDate.of(year, 12, 31);
		return tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
				.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(e -> VACATION_ENTITLEMENT.equals(e.getString(PARAM_VACATION_TYPE)))
				.filter(e -> {
					LocalDate date = e.getDate(PARAM_DATE).toLocalDate();
					return !date.isBefore(yearStart) && !date.isAfter(yearEnd);
				})
				.findFirst();
	}

	public static void assertSufficientVacationBalance(StrolchTransaction tx, String employeeId, int requestedMinutes,
			ZonedDateTime at) {
		int currentBalance = getVacationBalance(tx, employeeId, at);
		if (currentBalance < requestedMinutes) {
			throw new IllegalStateException(
					"Insufficient vacation balance for employee " + employeeId + ": requested "
							+ requestedMinutes + " minutes, but only " + currentBalance + " minutes available.");
		}
	}

	public static boolean isVacationAbsence(StrolchTransaction tx, Resource absence) {
		Resource absenceType = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
		if (absenceType.getBoolean(PARAM_REDUCE_VACATION_CREDIT)) {
			return true;
		}
		String code = absenceType.hasParameter(PARAM_CODE) ? absenceType.getString(PARAM_CODE) : absenceType.getId();
		return getVacationAbsenceTypeCode(tx).equalsIgnoreCase(code);
	}
}
