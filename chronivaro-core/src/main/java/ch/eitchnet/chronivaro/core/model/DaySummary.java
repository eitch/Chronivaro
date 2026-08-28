package ch.eitchnet.chronivaro.core.model;

import ch.eitchnet.chronivaro.core.service.PresenceService;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public record DaySummary(LocalDate date, DayState state, String stateLabel, int targetMinutes, int actualMinutes,
							 int holidayMinutes, int absenceMinutes, boolean isOff, WorkingLocation workingLocation,
							 List<WorkEntryRange> workEntries,
							 List<BreakRange> breaks,
							 ActiveTimer activeTimer) {

	public DaySummary(LocalDate date, DayState state, String stateLabel, int targetMinutes, int actualMinutes,
					  int holidayMinutes, int absenceMinutes, boolean isOff, WorkingLocation workingLocation,
					  List<WorkEntryRange> workEntries,
					  List<BreakRange> breaks) {
		this(date, state, stateLabel, targetMinutes, actualMinutes, holidayMinutes, absenceMinutes, isOff,
				workingLocation, workEntries, breaks, null);
	}

	public int getBalance() {
		return actualMinutes + holidayMinutes + absenceMinutes - targetMinutes;
	}

	public record ActiveTimer(String id, ZonedDateTime start, WorkingLocation workingLocation, boolean isPreviousDay) {
	}
}
