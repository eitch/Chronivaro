package ch.atexxi.chronivaro.rest.dto;

import java.time.LocalDate;
import java.util.List;

public record DaySummaryDto(LocalDate date, String state, int targetMinutes, int actualMinutes, int holidayMinutes,
                            int absenceMinutes, int balance, List<WorkEntryRangeDto> workEntries,
                            List<BreakRangeDto> breaks) {
}
