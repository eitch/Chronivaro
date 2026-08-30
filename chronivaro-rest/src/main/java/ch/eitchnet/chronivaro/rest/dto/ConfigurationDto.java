package ch.eitchnet.chronivaro.rest.dto;

public record ConfigurationDto(
		Integer weeklyTargetMinutes,
		Integer annualVacationDays,
		Integer minutesPerVacationDay,
		String vacationAbsenceTypeCode,
		String companyName,
		String companyLogo,
		String defaultLanguage,
		String serverBaseUrl,
		String officeHoursStart,
		String officeHoursEnd,
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
		this(weeklyTargetMinutes, annualVacationDays, minutesPerVacationDay, vacationAbsenceTypeCode, "Chronivaro", "", "de", "http://localhost:8080", "07:00", "18:00", version, updatedBy);
	}

	public ConfigurationDto(
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
		this(weeklyTargetMinutes, annualVacationDays, minutesPerVacationDay, vacationAbsenceTypeCode, companyName, companyLogo, defaultLanguage, "http://localhost:8080", "07:00", "18:00", version, updatedBy);
	}
}
