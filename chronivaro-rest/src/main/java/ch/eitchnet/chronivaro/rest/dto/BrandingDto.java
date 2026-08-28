package ch.eitchnet.chronivaro.rest.dto;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.DEFAULT_WEEKLY_TARGET_MINUTES;

public record BrandingDto(
		String companyName,
		String companyLogo,
		String defaultLanguage,
		Integer weeklyTargetMinutes
) {
	public BrandingDto(String companyName, String companyLogo, String defaultLanguage) {
		this(companyName, companyLogo, defaultLanguage, DEFAULT_WEEKLY_TARGET_MINUTES);
	}
}
