package ch.eitchnet.chronivaro.rest.dto;

import java.time.LocalDate;

public record DayStatusDto(
		LocalDate date,
		int targetMinutes,
		int actualMinutes,
		int dayBalanceMinutes
) {
}
