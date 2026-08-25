package ch.eitchnet.chronivaro.rest.dto;

public record PeriodActionRequestDto(
		String employeeId,
		String yearMonth,
		String comment
) {
}
