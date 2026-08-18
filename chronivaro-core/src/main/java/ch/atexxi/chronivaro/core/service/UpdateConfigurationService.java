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
	}

	@Override
	protected ServiceResult internalDoService(UpdateConfigurationArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", true);
			tx.readLock(config);

			if (arg.weeklyTargetMinutes != null) {
				config.setInteger(PARAM_WEEKLY_TARGET_MINUTES, arg.weeklyTargetMinutes);
			}

			bumpVersion(config, tx);
			tx.update(config);

			ChronivaroAuditHelper.audit(tx, TYPE_GLOBAL_CONFIGURATION, config.getId(), AUDIT_ACTION_UPDATE,
					"Updated global configuration weeklyTargetMinutes=" + arg.weeklyTargetMinutes);

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
