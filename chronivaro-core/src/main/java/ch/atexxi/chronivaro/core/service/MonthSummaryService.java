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
			int totalTarget = 0;
			int totalActual = 0;
			int totalHoliday = 0;
			int totalAbsence = 0;
			List<DaySummary> daySummaries = new ArrayList<>();

			DaySummaryService daySummaryService = new DaySummaryService();

			for (int day = 1; day <= arg.yearMonth.lengthOfMonth(); day++) {
				LocalDate date = arg.yearMonth.atDay(day);

				DaySummaryService.DaySummaryArgument dayArg = new DaySummaryService.DaySummaryArgument();
				dayArg.employeeId = arg.employeeId;
				dayArg.date = date;

				// We can call internalDoService directly if we share the transaction, but AbstractService.doService opens its own.
				// However, we want to stay in the same transaction.
				// Let's refactor DaySummary logic into a helper if needed, but for now I'll just call the service logic manually or reuse the helper methods.

				int target = ScheduleHelper.getTargetMinutes(tx, arg.employeeId, date);
				int holiday = HolidayHelper.getHolidayMinutes(tx, arg.employeeId, date);
				int absence = AbsenceHelper.getAbsenceMinutes(tx, arg.employeeId, date);

				// TODO: Calculate actual minutes for the day
				Resource employee = ChronivaroModelHelper.getEmployee(tx, arg.employeeId);
				java.time.ZonedDateTime from = date.atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(employee));
				java.time.ZonedDateTime to = date
						.plusDays(1)
						.atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(employee))
						.minusNanos(1);
				List<Resource> entries = WorkEntryHelper.findWorkEntries(tx, arg.employeeId, from, to);
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
						new DaySummary(date, state, state.getLabel(), target, actual, holiday, absence, List.of(),
								List.of()));
			}

			// TODO: Initial balance from previous period
			int initialBalance = 0;

			MonthSummary summary = new MonthSummary(arg.employeeId, arg.yearMonth, totalTarget, totalActual,
					totalHoliday, totalAbsence, initialBalance, daySummaries);
			return new MonthSummaryResult(summary);
		}
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
