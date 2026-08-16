package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.*;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.PARAM_END;
import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.PARAM_START;

public class DaySummaryService
		extends AbstractService<DaySummaryService.DaySummaryArgument, DaySummaryService.DaySummaryResult> {

	public static class DaySummaryArgument extends ServiceArgument {
		public String employeeId;
		public LocalDate date;
	}

	public static class DaySummaryResult extends ServiceResult {
		public DaySummary daySummary;

		public DaySummaryResult(DaySummary daySummary) {
			super(ServiceResult.success().getState());
			this.daySummary = daySummary;
		}

		public DaySummaryResult() {
		}
	}

	@Override
	protected DaySummaryResult internalDoService(DaySummaryArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.employeeId);
		DBC.PRE.assertNotNull("date must be set", arg.date);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource employee = ChronivaroModelHelper.getEmployee(tx, arg.employeeId);
			ZonedDateTime from = arg.date.atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(employee));
			ZonedDateTime to = arg.date.plusDays(1).atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(employee));

			int targetMinutes = ScheduleHelper.getTargetMinutes(tx, arg.employeeId, arg.date);
			int holidayMinutes = HolidayHelper.getHolidayMinutes(tx, arg.employeeId, arg.date);
			int absenceMinutes = AbsenceHelper.getAbsenceMinutes(tx, arg.employeeId, arg.date);

			List<Resource> entries = WorkEntryHelper.findWorkEntries(tx, arg.employeeId, from, to);
			List<WorkEntryRange> ranges = new ArrayList<>();
			List<BreakRange> breaks = new ArrayList<>();
			int actualMinutes = 0;
			DayState state = DayState.NOT_WORKING;

			DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

			ZonedDateTime lastEnd = null;

			for (Resource entry : entries) {
				ZonedDateTime start = entry.getDate(PARAM_START);
				ZonedDateTime end = entry.getDate(PARAM_END);
				boolean isActive = end.getYear() == 1970;
				if (isActive)
					state = DayState.WORKING;

				// Clip to day boundaries
				ZonedDateTime effectiveStart = start.isBefore(from) ? from : start;
				ZonedDateTime now = ZonedDateTime.now(effectiveStart.getZone());
				ZonedDateTime effectiveEnd = isActive ? (now.isBefore(to) ? now : to) : (end.isAfter(to) ? to : end);

				if (effectiveEnd.isBefore(effectiveStart))
					continue;

				int duration = (int) Duration.between(effectiveStart, effectiveEnd).toMinutes();
				actualMinutes += duration;

				ranges.add(new WorkEntryRange(entry.getId(), effectiveStart.format(timeFormatter),
						isActive ? "..." : effectiveEnd.format(timeFormatter), duration));

				if (lastEnd != null && start.isAfter(lastEnd)) {
					int breakDuration = (int) Duration.between(lastEnd, start).toMinutes();
					if (breakDuration > 0) {
						breaks.add(new BreakRange(lastEnd.format(timeFormatter), start.format(timeFormatter),
								breakDuration));
					}
				}
				lastEnd = end;
			}

			DaySummary summary = new DaySummary(arg.date, state, state.getLabel(), targetMinutes, actualMinutes,
					holidayMinutes, absenceMinutes, targetMinutes == 0, ranges, breaks);
			return new DaySummaryResult(summary);
		}
	}

	@Override
	public DaySummaryArgument getArgumentInstance() {
		return new DaySummaryArgument();
	}

	@Override
	public DaySummaryResult getResultInstance() {
		return new DaySummaryResult();
	}
}
