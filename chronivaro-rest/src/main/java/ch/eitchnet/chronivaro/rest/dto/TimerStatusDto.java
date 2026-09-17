package ch.eitchnet.chronivaro.rest.dto;

public record TimerStatusDto(
		boolean running,
		CurrentWorkEntryDto currentWorkEntry,
		DayStatusDto today,
		MonthStatusDto month
) {
}
