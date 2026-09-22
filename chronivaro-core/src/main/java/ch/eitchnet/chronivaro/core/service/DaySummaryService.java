package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.*;
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
import java.util.Optional;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.getVersion;

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
			DaySummary summary = getDaySummary(tx, arg.employeeId, arg.date);
			return new DaySummaryResult(summary);
		}
	}

	public static DaySummary getDaySummary(StrolchTransaction tx, String employeeId, LocalDate date) {
		Resource employee = ChronivaroModelHelper.getEmployee(tx, employeeId);
		ZonedDateTime from = date.atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(employee));
		ZonedDateTime to = date.plusDays(1).atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(employee));

		int targetMinutes = ScheduleHelper.getTargetMinutes(tx, employeeId, date);
		int holidayMinutes = HolidayHelper.getHolidayMinutes(tx, employeeId, date);
		int absenceMinutes = AbsenceHelper.getAbsenceMinutes(tx, employeeId, date);

		List<Resource> entries = WorkEntryHelper.findWorkEntries(tx, employeeId, from, to);
		List<WorkEntryRange> ranges = new ArrayList<>();
		List<BreakRange> breaks = new ArrayList<>();
		int actualMinutes = 0;
		DayState state = DayState.NOT_WORKING;
		WorkingLocation workingLocation = null;

		Optional<Resource> activeEntryOpt = WorkEntryHelper.findActiveWorkEntry(tx, employeeId);
		DaySummary.ActiveTimer activeTimer = null;
		if (activeEntryOpt.isPresent()) {
			Resource activeEntry = activeEntryOpt.get();
			ZonedDateTime activeStart = activeEntry.getDate(PARAM_START);
			LocalDate activeStartDate = activeStart.toLocalDate();
			WorkingLocation activeLoc = activeEntry.hasParameter(PARAM_WORKING_LOCATION) && !activeEntry
					.getString(PARAM_WORKING_LOCATION)
					.isBlank() ? WorkingLocation.valueOf(activeEntry.getString(PARAM_WORKING_LOCATION)) : null;

			if (activeStartDate.isEqual(date)) {
				activeTimer = new DaySummary.ActiveTimer(activeEntry.getId(), activeStart, activeLoc, false);
				state = DayState.WORKING;
				workingLocation = activeLoc;
			} else if (activeStartDate.isBefore(date)) {
				activeTimer = new DaySummary.ActiveTimer(activeEntry.getId(), activeStart, activeLoc, true);
				state = DayState.WORKING;
				workingLocation = activeLoc;
			}
		}

		DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

		ZonedDateTime lastEnd = null;

		for (Resource entry : entries) {
			ZonedDateTime start = entry.getDate(PARAM_START);
			ZonedDateTime end = entry.getDate(PARAM_END);
			boolean isActive = end.getYear() == 1970;

			if (isActive && start.toLocalDate().isBefore(date))
				continue;

			if (isActive) {
				state = DayState.WORKING;
				workingLocation = WorkingLocation.valueOf(entry.getString(PARAM_WORKING_LOCATION));
			}

			// Clip to day boundaries
			ZonedDateTime effectiveStart = start.isBefore(from) ? from : start;
			ZonedDateTime now = ZonedDateTime.now(effectiveStart.getZone());
			ZonedDateTime effectiveEnd = isActive ? (now.isBefore(to) ? now : to) : (end.isAfter(to) ? to : end);

			if (effectiveEnd.isBefore(effectiveStart))
				continue;

			int duration = (int) Duration.between(effectiveStart, effectiveEnd).toMinutes();
			actualMinutes += duration;

			String source = entry.hasParameter(PARAM_SOURCE) ? entry.getString(PARAM_SOURCE) : null;
			String createdBy = entry.hasParameter(PARAM_CREATED_BY) ? entry.getString(PARAM_CREATED_BY) : null;
			boolean modified = getVersion(entry) > 0;
			boolean isOnCall = entry.hasParameter(PARAM_IS_ON_CALL) && entry.getBoolean(PARAM_IS_ON_CALL);

			ranges.add(new WorkEntryRange(entry.getId(), effectiveStart.format(timeFormatter),
					isActive ? "..." : effectiveEnd.format(timeFormatter), duration, source, createdBy, modified,
					isOnCall));

			if (lastEnd != null && start.isAfter(lastEnd)) {
				int breakDuration = (int) Duration.between(lastEnd, start).toMinutes();
				if (breakDuration > 0) {
					breaks.add(
							new BreakRange(lastEnd.format(timeFormatter), start.format(timeFormatter), breakDuration));
				}
			}
			lastEnd = end;
		}

		return new DaySummary(date, state, state.getLabel(), targetMinutes, actualMinutes, holidayMinutes,
				absenceMinutes, targetMinutes == 0, workingLocation, ranges, breaks, activeTimer);
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
