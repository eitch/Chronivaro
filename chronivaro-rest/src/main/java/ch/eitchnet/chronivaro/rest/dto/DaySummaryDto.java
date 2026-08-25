package ch.eitchnet.chronivaro.rest.dto;

import ch.eitchnet.chronivaro.core.model.DayState;
import ch.eitchnet.chronivaro.core.model.WorkingLocation;
import ch.eitchnet.chronivaro.core.service.PresenceService;

import java.time.LocalDate;
import java.util.List;

public record DaySummaryDto(LocalDate date, DayState state, String stateLabel, int targetMinutes, int actualMinutes, int holidayMinutes,
							int absenceMinutes, boolean isOff, int balance, WorkingLocation workingLocation,
							List<WorkEntryRangeDto> workEntries,
							List<BreakRangeDto> breaks) {
}
