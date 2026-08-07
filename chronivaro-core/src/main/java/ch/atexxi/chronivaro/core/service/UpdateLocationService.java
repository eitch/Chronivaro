package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.ServiceResult;
import li.strolch.service.api.AbstractService;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class UpdateLocationService extends AbstractService<CreateLocationService.LocationArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(CreateLocationService.LocationArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource location = ChronivaroModelHelper.getLocation(tx, arg.id);
			location.setName(arg.name);
			location.setString(PARAM_NAME, arg.name);
			location.setString(PARAM_TIMEZONE, arg.timezone);
			location.setString(BAG_RELATIONS, TYPE_HOLIDAY_CALENDAR, arg.holidayCalendarId);
			tx.update(location);
			tx.commitOnClose();
		}
		return ServiceResult.success();
	}

	@Override
	public CreateLocationService.LocationArgument getArgumentInstance() {
		return new CreateLocationService.LocationArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
