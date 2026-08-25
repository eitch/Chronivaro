package ch.eitchnet.chronivaro.core.model;

import ch.eitchnet.chronivaro.core.service.PresenceService;

import java.time.LocalDate;
import java.util.List;

public record DaySummary(LocalDate date, DayState state, String stateLabel, int targetMinutes, int actualMinutes,
							 int holidayMinutes, int absenceMinutes, boolean isOff, WorkingLocation workingLocation,
							 List<WorkEntryRange> workEntries,
						 List<BreakRange> breaks) {
	public int getBalance() {
		return actualMinutes + holidayMinutes + absenceMinutes - targetMinutes;
	}
}
