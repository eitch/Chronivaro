package ch.eitchnet.chronivaro.rest.dto;

import java.time.YearMonth;

public record MonthStatusDto(
		YearMonth yearMonth,
		int targetMinutesToDate,
		int actualMinutesToDate,
		int periodBalanceMinutes
) {
}
