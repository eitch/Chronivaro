package ch.atexxi.chronivaro.rest.dto;

import ch.atexxi.chronivaro.core.model.DayState;
import ch.atexxi.chronivaro.core.service.PresenceService;

import java.time.LocalDate;
import java.util.List;

public record DaySummaryDto(LocalDate date, DayState state, String stateLabel, int targetMinutes, int actualMinutes, int holidayMinutes,
							int absenceMinutes, int balance, List<WorkEntryRangeDto> workEntries,
							List<BreakRangeDto> breaks) {
}
