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
import java.util.ArrayList;
import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.PARAM_END;
import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.PARAM_START;

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
			MonthSummary summary = calculateMonthSummary(tx, arg.employeeId, arg.yearMonth);
			return new MonthSummaryResult(summary);
		}
	}

	public static MonthSummary calculateMonthSummary(StrolchTransaction tx, String employeeId, YearMonth yearMonth) {
		int totalTarget = 0;
		int totalActual = 0;
		int totalHoliday = 0;
		int totalAbsence = 0;
		List<DaySummary> daySummaries = new ArrayList<>();

		for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
			LocalDate date = yearMonth.atDay(day);

			int target = ScheduleHelper.getTargetMinutes(tx, employeeId, date);
			int holiday = HolidayHelper.getHolidayMinutes(tx, employeeId, date);
			int absence = AbsenceHelper.getAbsenceMinutes(tx, employeeId, date);

			Resource employee = ChronivaroModelHelper.getEmployee(tx, employeeId);
			java.time.ZonedDateTime from = date.atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(employee));
			java.time.ZonedDateTime to = date
					.plusDays(1)
					.atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(employee))
					.minusNanos(1);
			List<Resource> entries = WorkEntryHelper.findWorkEntries(tx, employeeId, from, to);
			int actual = 0;
			DayState state = DayState.NOT_WORKING;
			for (Resource entry : entries) {
				java.time.ZonedDateTime start = entry.getDate(PARAM_START);
				java.time.ZonedDateTime end = entry.getDate(PARAM_END);
				boolean isActive = end.getYear() == 1970;
				if (isActive)
					state = DayState.WORKING;

				java.time.ZonedDateTime effectiveStart = start.isBefore(from) ? from : start;
				java.time.ZonedDateTime now = java.time.ZonedDateTime.now(effectiveStart.getZone());
				java.time.ZonedDateTime effectiveEnd = isActive ? (now.isBefore(to) ? now : to) :
						(end.isAfter(to) ? to : end);
				if (effectiveEnd.isAfter(effectiveStart)) {
					actual += (int) java.time.Duration.between(effectiveStart, effectiveEnd).toMinutes();
				}
			}

			totalTarget += target;
			totalActual += actual;
			totalHoliday += holiday;
			totalAbsence += absence;

			daySummaries.add(
					new DaySummary(date, state, state.getLabel(), target, actual, holiday, absence, target == 0, null,
							List.of(), List.of()));
		}

		int initialBalance = 0;

		return new MonthSummary(employeeId, yearMonth, totalTarget, totalActual, totalHoliday, totalAbsence,
				initialBalance, daySummaries);
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
