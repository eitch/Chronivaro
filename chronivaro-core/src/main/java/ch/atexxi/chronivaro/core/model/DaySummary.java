package ch.atexxi.chronivaro.core.model;

import java.time.LocalDate;
import java.util.List;

public record DaySummary(LocalDate date, DayState state, int targetMinutes, int actualMinutes, int holidayMinutes,
                         int absenceMinutes, List<WorkEntryRange> workEntries, List<BreakRange> breaks) {
	public int getBalance() {
		return actualMinutes + holidayMinutes + absenceMinutes - targetMinutes;
	}
}
