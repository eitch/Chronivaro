package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;

public class UpdateLocationService
		extends AbstractService<CreateLocationService.UpdateLocationArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(CreateLocationService.UpdateLocationArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource location = ChronivaroModelHelper.getLocation(tx, arg.id);
			location.setName(arg.name);
			location.setString(PARAM_NAME, arg.name);
			location.setString(PARAM_TIMEZONE, arg.timezone);
			location.setRelation(PARAM_HOLIDAY_CALENDAR,
					tx.getResourceBy(TYPE_HOLIDAY_CALENDAR, arg.holidayCalendarId, true));
			bumpVersion(location, tx);
			tx.update(location);
			ChronivaroAuditHelper.audit(tx, TYPE_LOCATION, location.getId(), AUDIT_ACTION_UPDATE,
					"Updated location " + arg.name);
			tx.commitOnClose();
		}
		return ServiceResult.success();
	}

	@Override
	public CreateLocationService.UpdateLocationArgument getArgumentInstance() {
		return new CreateLocationService.UpdateLocationArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
