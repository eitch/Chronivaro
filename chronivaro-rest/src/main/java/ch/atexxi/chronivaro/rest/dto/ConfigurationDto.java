package ch.atexxi.chronivaro.rest.dto;

public record ConfigurationDto(
		Integer weeklyTargetMinutes,
		Integer annualVacationDays,
		Integer minutesPerVacationDay,
		String vacationAbsenceTypeCode,
		Integer version,
		String updatedBy
) {
}
