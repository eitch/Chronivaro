package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import java.net.URI;
import java.util.Base64;
import java.util.Set;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;

public class UpdateConfigurationService extends AbstractService<UpdateConfigurationService.UpdateConfigurationArgument, ServiceResult> {

	private static final int MAX_LOGO_DATA_URI_LENGTH = 7_000_000; // ~5MB base64 payload
	private static final Set<String> SUPPORTED_IMAGE_MIME_TYPES = Set.of(
			"image/png",
			"image/jpeg",
			"image/jpg",
			"image/svg+xml",
			"image/gif",
			"image/webp",
			"image/x-icon"
	);

	public static class UpdateConfigurationArgument extends ServiceArgument {
		public Integer weeklyTargetMinutes;
		public Integer annualVacationDays;
		public Integer minutesPerVacationDay;
		public String vacationAbsenceTypeCode;
		public String companyName;
		public String companyLogo;
		public String defaultLanguage;
	}

	@Override
	protected ServiceResult internalDoService(UpdateConfigurationArgument arg) throws Exception {
		if (arg.weeklyTargetMinutes != null && (arg.weeklyTargetMinutes < 0 || arg.weeklyTargetMinutes > 10080)) {
			throw new IllegalArgumentException("weeklyTargetMinutes must be between 0 and 10080");
		}
		if (arg.annualVacationDays != null && (arg.annualVacationDays < 0 || arg.annualVacationDays > 365)) {
			throw new IllegalArgumentException("annualVacationDays must be between 0 and 365");
		}
		if (arg.minutesPerVacationDay != null && (arg.minutesPerVacationDay <= 0 || arg.minutesPerVacationDay > 1440)) {
			throw new IllegalArgumentException("minutesPerVacationDay must be between 1 and 1440");
		}
		if (arg.vacationAbsenceTypeCode != null && arg.vacationAbsenceTypeCode.isBlank()) {
			throw new IllegalArgumentException("vacationAbsenceTypeCode cannot be blank");
		}
		if (arg.companyName != null && arg.companyName.length() > 200) {
			throw new IllegalArgumentException("companyName cannot exceed 200 characters");
		}
		if (arg.defaultLanguage != null && !arg.defaultLanguage.isBlank()) {
			String lang = arg.defaultLanguage.trim().toLowerCase();
			if (!"de".equals(lang) && !"en".equals(lang)) {
				throw new IllegalArgumentException("defaultLanguage must be either 'de' or 'en'");
			}
		}
		if (arg.companyLogo != null && !arg.companyLogo.isBlank() && !isValidLogo(arg.companyLogo)) {
			throw new IllegalArgumentException("companyLogo must be a valid image URL, relative image path, or data URI");
		}

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", true);
			tx.readLock(config);

			StringBuilder changes = new StringBuilder("Updated global configuration");
			if (arg.weeklyTargetMinutes != null) {
				config.setInteger(PARAM_WEEKLY_TARGET_MINUTES, arg.weeklyTargetMinutes);
				changes.append(" weeklyTargetMinutes=").append(arg.weeklyTargetMinutes);
			}
			if (arg.annualVacationDays != null) {
				config.setInteger(PARAM_ANNUAL_VACATION_DAYS, arg.annualVacationDays);
				changes.append(" annualVacationDays=").append(arg.annualVacationDays);
			}
			if (arg.minutesPerVacationDay != null) {
				config.setInteger(PARAM_MINUTES_PER_VACATION_DAY, arg.minutesPerVacationDay);
				changes.append(" minutesPerVacationDay=").append(arg.minutesPerVacationDay);
			}
			if (arg.vacationAbsenceTypeCode != null && !arg.vacationAbsenceTypeCode.isBlank()) {
				config.setString(PARAM_VACATION_ABSENCE_TYPE_CODE, arg.vacationAbsenceTypeCode);
				changes.append(" vacationAbsenceTypeCode=").append(arg.vacationAbsenceTypeCode);
			}
			if (arg.companyName != null) {
				String name = arg.companyName.trim();
				config.setString(PARAM_COMPANY_NAME, name);
				changes.append(" companyName=").append(name);
			}
			if (arg.companyLogo != null) {
				String logo = arg.companyLogo.trim();
				config.setString(PARAM_COMPANY_LOGO, logo);
				changes.append(" companyLogo=").append(logo.isEmpty() ? "(cleared)" : "(set)");
			}
			if (arg.defaultLanguage != null && !arg.defaultLanguage.isBlank()) {
				String lang = arg.defaultLanguage.trim().toLowerCase();
				config.setString(PARAM_DEFAULT_LANGUAGE, lang);
				changes.append(" defaultLanguage=").append(lang);
			}

			bumpVersion(config, tx);
			tx.update(config);

			ChronivaroAuditHelper.audit(tx, TYPE_GLOBAL_CONFIGURATION, config.getId(), AUDIT_ACTION_UPDATE,
					changes.toString());

			tx.commitOnClose();
		}
		return ServiceResult.success();
	}

	private static boolean isValidLogo(String logo) {
		if (logo == null || logo.isBlank()) {
			return true;
		}
		String trimmed = logo.trim();
		if (trimmed.startsWith("data:")) {
			if (trimmed.length() > MAX_LOGO_DATA_URI_LENGTH) {
				return false;
			}
			int semicolonIdx = trimmed.indexOf(";base64,");
			if (semicolonIdx <= 5) {
				return false;
			}
			String mimeType = trimmed.substring(5, semicolonIdx).trim().toLowerCase();
			if (!SUPPORTED_IMAGE_MIME_TYPES.contains(mimeType)) {
				return false;
			}
			String base64Payload = trimmed.substring(semicolonIdx + 8);
			if (base64Payload.isBlank()) {
				return false;
			}
			try {
				Base64.getDecoder().decode(base64Payload);
				return true;
			} catch (IllegalArgumentException e) {
				return false;
			}
		}
		if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
			try {
				new URI(trimmed);
				return true;
			} catch (Exception e) {
				return false;
			}
		}
		if (trimmed.startsWith("/") || trimmed.startsWith("assets/") || trimmed.startsWith("images/")) {
			return true;
		}
		String lower = trimmed.toLowerCase();
		return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
				|| lower.endsWith(".svg") || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".ico");
	}

	@Override
	public UpdateConfigurationArgument getArgumentInstance() {
		return new UpdateConfigurationArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
