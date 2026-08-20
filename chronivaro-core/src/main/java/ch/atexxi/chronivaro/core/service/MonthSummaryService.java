package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.*;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.utils.helper.StringHelper.isNotEmpty;

public class MonthSummaryService
		extends AbstractService<MonthSummaryService.MonthSummaryArgument, MonthSummaryService.MonthSummaryResult> {

	public static class MonthSummaryArgument extends ServiceArgument {
		public String employeeId;
		public YearMonth yearMonth;
	}

	public static class MonthSummaryResult extends ServiceResult {
		public MonthSummary monthSummary;

		public MonthSummaryResult(MonthSummary monthSummary) {
			super(ServiceResult.success().getState());
			this.monthSummary = monthSummary;
		}

		public MonthSummaryResult() {
		}
	}

	@Override
	protected MonthSummaryResult internalDoService(MonthSummaryArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.employeeId);
		DBC.PRE.assertNotNull("yearMonth must be set", arg.yearMonth);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			MonthSummary summary = getMonthSummary(tx, arg.employeeId, arg.yearMonth);
			return new MonthSummaryResult(summary);
		}
	}

	public static MonthSummary getMonthSummary(StrolchTransaction tx, String employeeId, YearMonth yearMonth) {
		Optional<Resource> periodOpt = PeriodHelper.findPeriod(tx, employeeId, yearMonth);
		if (periodOpt.isPresent()) {
			Resource period = periodOpt.get();
			String state = period.getString(PARAM_STATE);
			if ((STATE_APPROVED.equals(state) || STATE_LOCKED.equals(state)) && period.hasParameter(PARAM_CALCULATION_SNAPSHOT)) {
				String snapshot = period.getString(PARAM_CALCULATION_SNAPSHOT);
				if (isNotEmpty(snapshot)) {
					return PeriodHelper.parseCalculationSnapshot(snapshot);
				}
			}
		}
		return calculateMonthSummary(tx, employeeId, yearMonth);
	}

	public static MonthSummary calculateMonthSummary(StrolchTransaction tx, String employeeId, YearMonth yearMonth) {
		int totalTarget = 0;
		int totalActual = 0;
		int totalPaidAbsence = 0;
		int totalUnpaidAbsence = 0;
		int totalVacationAbsence = 0;
		int totalHoliday = 0;
		List<DaySummary> daySummaries = new ArrayList<>();

		Resource employee = ChronivaroModelHelper.getEmployee(tx, employeeId);
		ZoneId zone = ChronivaroModelHelper.getEmployeeTimezone(employee);

		for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
			LocalDate date = yearMonth.atDay(day);

			int target = ScheduleHelper.getTargetMinutes(tx, employeeId, date);
			int holiday = HolidayHelper.getHolidayMinutes(tx, employeeId, date);
			AbsenceHelper.DayAbsenceBreakdown breakdown = AbsenceHelper.getDayAbsenceBreakdown(tx, employeeId, date);
			int creditedAbsence = breakdown.totalCreditedMinutes();

			ZonedDateTime from = date.atStartOfDay(zone);
			ZonedDateTime to = date
					.plusDays(1)
					.atStartOfDay(zone)
					.minusNanos(1);
			List<Resource> entries = WorkEntryHelper.findWorkEntries(tx, employeeId, from, to);
			int actual = 0;
			DayState state = DayState.NOT_WORKING;
			WorkingLocation workingLocation = null;

			for (Resource entry : entries) {
				ZonedDateTime start = entry.getDate(PARAM_START);
				ZonedDateTime end = entry.getDate(PARAM_END);
				boolean isActive = end.getYear() == 1970;
				if (isActive)
					state = DayState.WORKING;

				ZonedDateTime effectiveStart = start.isBefore(from) ? from : start;
				ZonedDateTime now = ZonedDateTime.now(effectiveStart.getZone());
				ZonedDateTime effectiveEnd = isActive ? (now.isBefore(to) ? now : to) :
						(end.isAfter(to) ? to : end);
				if (effectiveEnd.isAfter(effectiveStart)) {
					actual += (int) java.time.Duration.between(effectiveStart, effectiveEnd).toMinutes();
				}
				if (workingLocation == null && entry.hasParameter(PARAM_WORKING_LOCATION)) {
					try {
						workingLocation = WorkingLocation.valueOf(entry.getString(PARAM_WORKING_LOCATION));
					} catch (Exception ignored) {
					}
				}
			}

			totalTarget += target;
			totalActual += actual;
			totalPaidAbsence += breakdown.paidMinutes();
			totalUnpaidAbsence += breakdown.unpaidMinutes();
			totalVacationAbsence += breakdown.vacationMinutes();
			totalHoliday += holiday;

			daySummaries.add(
					new DaySummary(date, state, state.getLabel(), target, actual, holiday, creditedAbsence, target == 0,
							workingLocation, List.of(), List.of()));
		}

		int totalCreditedAbsence = totalPaidAbsence + totalVacationAbsence;
		int initialBalance = calculateInitialBalance(tx, employeeId, yearMonth);
		int manualCorrections = 0;

		return new MonthSummary(employeeId, yearMonth, totalTarget, totalActual, totalPaidAbsence, totalUnpaidAbsence,
				totalVacationAbsence, totalHoliday, totalCreditedAbsence, initialBalance, manualCorrections, daySummaries);
	}

	public static int calculateInitialBalance(StrolchTransaction tx, String employeeId, YearMonth targetYearMonth) {
		Resource employee = ChronivaroModelHelper.getEmployee(tx, employeeId);
		LocalDate joinDate = ChronivaroModelHelper.getJoinDate(employee);
		YearMonth joinYearMonth = YearMonth.from(joinDate);

		if (!targetYearMonth.isAfter(joinYearMonth)) {
			return 0;
		}

		// Look backwards from targetYearMonth - 1 to find the nearest approved/locked period snapshot
		YearMonth checkYm = targetYearMonth.minusMonths(1);
		YearMonth snapshotYm = null;
		int startingBalance = 0;

		while (!checkYm.isBefore(joinYearMonth)) {
			Optional<Resource> periodOpt = PeriodHelper.findPeriod(tx, employeeId, checkYm);
			if (periodOpt.isPresent()) {
				Resource period = periodOpt.get();
				String state = period.getString(PARAM_STATE);
				if ((STATE_APPROVED.equals(state) || STATE_LOCKED.equals(state)) && period.hasParameter(PARAM_CALCULATION_SNAPSHOT)) {
					String snapshot = period.getString(PARAM_CALCULATION_SNAPSHOT);
					if (isNotEmpty(snapshot)) {
						MonthSummary snapSummary = PeriodHelper.parseCalculationSnapshot(snapshot);
						startingBalance = snapSummary.getEndBalance();
						snapshotYm = checkYm;
						break;
					}
				}
			}
			checkYm = checkYm.minusMonths(1);
		}

		YearMonth startMonth = snapshotYm != null ? snapshotYm.plusMonths(1) : joinYearMonth;
		int accumulatedBalance = startingBalance;

		for (YearMonth ym = startMonth; ym.isBefore(targetYearMonth); ym = ym.plusMonths(1)) {
			accumulatedBalance += calculateMonthNetVariance(tx, employee, ym);
		}

		return accumulatedBalance;
	}

	private static int calculateMonthNetVariance(StrolchTransaction tx, Resource employee, YearMonth yearMonth) {
		String employeeId = employee.getId();
		ZoneId zone = ChronivaroModelHelper.getEmployeeTimezone(employee);

		int target = 0;
		int actual = 0;
		int holiday = 0;
		int creditedAbsence = 0;

		for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
			LocalDate date = yearMonth.atDay(day);
			target += ScheduleHelper.getTargetMinutes(tx, employeeId, date);
			holiday += HolidayHelper.getHolidayMinutes(tx, employeeId, date);
			creditedAbsence += AbsenceHelper.getAbsenceMinutes(tx, employeeId, date);

			ZonedDateTime from = date.atStartOfDay(zone);
			ZonedDateTime to = date.plusDays(1).atStartOfDay(zone).minusNanos(1);
			List<Resource> entries = WorkEntryHelper.findWorkEntries(tx, employeeId, from, to);
			for (Resource entry : entries) {
				ZonedDateTime start = entry.getDate(PARAM_START);
				ZonedDateTime end = entry.getDate(PARAM_END);
				boolean isActive = end.getYear() == 1970;
				ZonedDateTime effectiveStart = start.isBefore(from) ? from : start;
				ZonedDateTime now = ZonedDateTime.now(effectiveStart.getZone());
				ZonedDateTime effectiveEnd = isActive ? (now.isBefore(to) ? now : to) :
						(end.isAfter(to) ? to : end);
				if (effectiveEnd.isAfter(effectiveStart)) {
					actual += (int) java.time.Duration.between(effectiveStart, effectiveEnd).toMinutes();
				}
			}
		}

		return actual + holiday + creditedAbsence - target;
	}

	@Override
	public MonthSummaryArgument getArgumentInstance() {
		return new MonthSummaryArgument();
	}

	@Override
	public MonthSummaryResult getResultInstance() {
		return new MonthSummaryResult();
	}
}
