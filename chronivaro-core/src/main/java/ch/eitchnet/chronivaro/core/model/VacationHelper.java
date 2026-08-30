package ch.eitchnet.chronivaro.core.model;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.OptionalInt;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;

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
		OptionalInt latestCarryOverYear = tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
				.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(e -> VACATION_CARRY_OVER.equals(e.getString(PARAM_VACATION_TYPE)))
				.filter(e -> !e.getDate(PARAM_DATE).isAfter(at))
				.mapToInt(e -> e.getDate(PARAM_DATE).getYear())
				.max();

		if (latestCarryOverYear.isPresent()) {
			int cutoffYear = latestCarryOverYear.getAsInt();
			LocalDate cutoffDate = LocalDate.of(cutoffYear, 1, 1);
			return tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
					.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.filter(e -> !e.getDate(PARAM_DATE).isAfter(at))
					.filter(e -> !e.getDate(PARAM_DATE).toLocalDate().isBefore(cutoffDate))
					.mapToInt(e -> e.getInteger(PARAM_VALUE))
					.sum();
		}

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

		int carryOverEntriesSum = tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
				.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(e -> VACATION_CARRY_OVER.equals(e.getString(PARAM_VACATION_TYPE)))
				.filter(e -> {
					LocalDate date = e.getDate(PARAM_DATE).toLocalDate();
					return !date.isBefore(yearStart) && !date.isAfter(yearEnd);
				})
				.mapToInt(e -> e.getInteger(PARAM_VALUE))
				.sum();

		int carryOverAdjustmentsSum = tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
				.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(e -> VACATION_CORRECTION.equals(e.getString(PARAM_VACATION_TYPE)))
				.filter(e -> {
					LocalDate date = e.getDate(PARAM_DATE).toLocalDate();
					return !date.isBefore(yearStart) && !date.isAfter(yearEnd);
				})
				.filter(e -> e.hasParameter(PARAM_COMMENT) && e.getString(PARAM_COMMENT).startsWith("Carry-over adjustment"))
				.mapToInt(e -> e.getInteger(PARAM_VALUE))
				.sum();

		boolean hasCarryOverEntry = tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
				.anyMatch(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId)
						&& VACATION_CARRY_OVER.equals(e.getString(PARAM_VACATION_TYPE))
						&& !e.getDate(PARAM_DATE).toLocalDate().isBefore(yearStart)
						&& !e.getDate(PARAM_DATE).toLocalDate().isAfter(yearEnd));

		int carryOverMinutes;
		if (hasCarryOverEntry) {
			carryOverMinutes = carryOverEntriesSum + carryOverAdjustmentsSum;
		} else {
			carryOverMinutes = tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
					.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.filter(e -> e.getDate(PARAM_DATE).toLocalDate().isBefore(yearStart))
					.mapToInt(e -> e.getInteger(PARAM_VALUE))
					.sum();
		}

		int entitlementEntriesSum = tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
				.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(e -> VACATION_ENTITLEMENT.equals(e.getString(PARAM_VACATION_TYPE)))
				.filter(e -> {
					LocalDate date = e.getDate(PARAM_DATE).toLocalDate();
					return !date.isBefore(yearStart) && !date.isAfter(yearEnd);
				})
				.mapToInt(e -> e.getInteger(PARAM_VALUE))
				.sum();

		int entitlementRecalcSum = getEntitlementAdjustmentCorrections(tx, employeeId, year);
		int entitlementMinutes = entitlementEntriesSum + entitlementRecalcSum;

		int correctionsMinutes = tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
				.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(e -> VACATION_CORRECTION.equals(e.getString(PARAM_VACATION_TYPE)))
				.filter(e -> {
					LocalDate date = e.getDate(PARAM_DATE).toLocalDate();
					return !date.isBefore(yearStart) && !date.isAfter(yearEnd);
				})
				.filter(e -> !e.hasParameter(PARAM_COMMENT) || (!e.getString(PARAM_COMMENT).startsWith("Carry-over adjustment")
						&& !e.getString(PARAM_COMMENT).startsWith("Recalculated vacation entitlement")))
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

	public static java.util.List<Resource> getVacationEntries(StrolchTransaction tx, String employeeId, int year) {
		LocalDate yearStart = LocalDate.of(year, 1, 1);
		LocalDate yearEnd = LocalDate.of(year, 12, 31);
		return tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
				.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(e -> {
					LocalDate date = e.getDate(PARAM_DATE).toLocalDate();
					return !date.isBefore(yearStart) && !date.isAfter(yearEnd);
				})
				.sorted(java.util.Comparator.comparing(e -> e.getDate(PARAM_DATE)))
				.toList();
	}

	public static java.util.List<Resource> getAllVacationEntries(StrolchTransaction tx, String employeeId) {
		return tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
				.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.sorted(java.util.Comparator.comparing(e -> e.getDate(PARAM_DATE)))
				.toList();
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

	public static Optional<Resource> findCarryOverEntry(StrolchTransaction tx, String employeeId, int targetYear) {
		LocalDate yearStart = LocalDate.of(targetYear, 1, 1);
		LocalDate yearEnd = LocalDate.of(targetYear, 12, 31);
		return tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
				.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(e -> VACATION_CARRY_OVER.equals(e.getString(PARAM_VACATION_TYPE)))
				.filter(e -> {
					LocalDate date = e.getDate(PARAM_DATE).toLocalDate();
					return !date.isBefore(yearStart) && !date.isAfter(yearEnd);
				})
				.findFirst();
	}

	public static int getEntitlementAdjustmentCorrections(StrolchTransaction tx, String employeeId, int year) {
		LocalDate yearStart = LocalDate.of(year, 1, 1);
		LocalDate yearEnd = LocalDate.of(year, 12, 31);
		return tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
				.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(e -> VACATION_CORRECTION.equals(e.getString(PARAM_VACATION_TYPE)))
				.filter(e -> {
					LocalDate date = e.getDate(PARAM_DATE).toLocalDate();
					return !date.isBefore(yearStart) && !date.isAfter(yearEnd);
				})
				.filter(e -> e.hasParameter(PARAM_COMMENT) && e.getString(PARAM_COMMENT).startsWith("Recalculated vacation entitlement"))
				.mapToInt(e -> e.getInteger(PARAM_VALUE))
				.sum();
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

	public static Optional<String> creditOrRecalculateEntitlement(StrolchTransaction tx, String employeeId, int year,
			boolean forceRecalculate) {
		return creditOrRecalculateEntitlement(tx, employeeId, year, forceRecalculate, null);
	}

	public static Optional<String> creditOrRecalculateEntitlement(StrolchTransaction tx, String employeeId, int year,
			boolean forceRecalculate, String reason) {
		Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);

		int entitlementMinutes = calculateAnnualEntitlement(tx, employeeId, year);
		Optional<Resource> existing = findEntitlementEntry(tx, employeeId, year);

		String username = tx.getCertificate() != null ? tx.getCertificate().getUsername() : "system";

		if (existing.isPresent()) {
			Resource entry = existing.get();
			if (forceRecalculate) {
				int currentCredited = entry.getInteger(PARAM_VALUE)
						+ getEntitlementAdjustmentCorrections(tx, employeeId, year);
				int delta = entitlementMinutes - currentCredited;
				if (delta != 0) {
					String empName = employee.hasParameter(PARAM_FIRSTNAME) && employee.hasParameter(PARAM_LASTNAME)
							? employee.getString(PARAM_FIRSTNAME) + " " + employee.getString(PARAM_LASTNAME)
							: employee.getName();
					Resource corr = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
					corr.setName("Vacation Entitlement Recalculation " + year + " (" + empName + ")");
					corr.setRelation(PARAM_EMPLOYEE, employee);
					corr.setString(PARAM_VACATION_TYPE, VACATION_CORRECTION);
					LocalDate joinDate = ChronivaroModelHelper.getJoinDate(employee);
					LocalDate creditDate = joinDate.isAfter(LocalDate.of(year, 1, 1)) ? joinDate : LocalDate.of(year, 1, 1);
					corr.setDate(PARAM_DATE, creditDate.atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(employee)));
					corr.setInteger(PARAM_VALUE, delta);
					String reasonDetail = (reason != null && !reason.isBlank()) ? " due to " + reason : "";
					corr.setString(PARAM_COMMENT, "Recalculated vacation entitlement adjustment for year " + year
							+ reasonDetail + " (" + (delta > 0 ? "+" + delta : String.valueOf(delta)) + " minutes)");
					corr.setString(PARAM_CREATED_BY, username);

					ChronivaroVersionHelper.initVersion(corr, tx);
					tx.add(corr);

					ChronivaroAuditHelper.audit(tx, TYPE_VACATION_ACCOUNT_ENTRY, corr.getId(), AUDIT_ACTION_CREATE,
							"Recalculated vacation entitlement adjustment for year " + year + reasonDetail + " from "
									+ currentCredited + " to " + entitlementMinutes + " minutes (delta: " + delta + ")");
					return Optional.of(corr.getId());
				}
			}
			return Optional.of(entry.getId());
		} else {
			Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
			String empName = employee.hasParameter(PARAM_FIRSTNAME) && employee.hasParameter(PARAM_LASTNAME)
					? employee.getString(PARAM_FIRSTNAME) + " " + employee.getString(PARAM_LASTNAME)
					: employee.getName();
			entry.setName("Vacation Entitlement " + year + " (" + empName + ")");
			entry.setRelation(PARAM_EMPLOYEE, employee);
			entry.setString(PARAM_VACATION_TYPE, VACATION_ENTITLEMENT);
			LocalDate joinDate = ChronivaroModelHelper.getJoinDate(employee);
			LocalDate creditDate = joinDate.isAfter(LocalDate.of(year, 1, 1)) ? joinDate : LocalDate.of(year, 1, 1);
			entry.setDate(PARAM_DATE, creditDate.atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(employee)));
			entry.setInteger(PARAM_VALUE, entitlementMinutes);
			entry.setString(PARAM_COMMENT, "Annual vacation entitlement " + year);
			entry.setString(PARAM_CREATED_BY, username);

			ChronivaroVersionHelper.initVersion(entry, tx);
			tx.add(entry);

			ChronivaroAuditHelper.audit(tx, TYPE_VACATION_ACCOUNT_ENTRY, entry.getId(), AUDIT_ACTION_CREATE,
					"Credited annual vacation entitlement for year " + year + " (" + entitlementMinutes + " minutes)");
			return Optional.of(entry.getId());
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
