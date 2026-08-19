package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;

public class UpdateConfigurationService extends AbstractService<UpdateConfigurationService.UpdateConfigurationArgument, ServiceResult> {

	public static class UpdateConfigurationArgument extends ServiceArgument {
		public Integer weeklyTargetMinutes;
		public Integer annualVacationDays;
		public Integer minutesPerVacationDay;
		public String vacationAbsenceTypeCode;
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

			bumpVersion(config, tx);
			tx.update(config);

			ChronivaroAuditHelper.audit(tx, TYPE_GLOBAL_CONFIGURATION, config.getId(), AUDIT_ACTION_UPDATE,
					changes.toString());

			tx.commitOnClose();
		}
		return ServiceResult.success();
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
