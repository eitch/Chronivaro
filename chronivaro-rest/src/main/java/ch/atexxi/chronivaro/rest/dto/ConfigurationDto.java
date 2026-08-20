package ch.atexxi.chronivaro.rest.dto;

public record ConfigurationDto(
		Integer weeklyTargetMinutes,
		Integer annualVacationDays,
		Integer minutesPerVacationDay,
		String vacationAbsenceTypeCode,
		String companyName,
		String companyLogo,
		String defaultLanguage,
		Integer version,
		String updatedBy
) {
	public ConfigurationDto(
			Integer weeklyTargetMinutes,
			Integer annualVacationDays,
			Integer minutesPerVacationDay,
			String vacationAbsenceTypeCode,
			Integer version,
			String updatedBy
	) {
		this(weeklyTargetMinutes, annualVacationDays, minutesPerVacationDay, vacationAbsenceTypeCode, "Chronivaro", "", "de", version, updatedBy);
	}
}
