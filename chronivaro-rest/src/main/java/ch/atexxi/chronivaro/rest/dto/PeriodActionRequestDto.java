package ch.atexxi.chronivaro.rest.dto;

public record PeriodActionRequestDto(
		String employeeId,
		String yearMonth,
		String comment
) {
}
