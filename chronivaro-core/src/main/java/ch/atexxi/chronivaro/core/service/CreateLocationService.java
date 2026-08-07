package ch.atexxi.chronivaro.core.service;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.ServiceResult;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class CreateLocationService extends AbstractService<CreateLocationService.LocationArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(LocationArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource location = new Resource(arg.id, arg.name, TYPE_LOCATION);
			location.addParameterBag(new li.strolch.model.ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			location.addParameterBag(new li.strolch.model.ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			location.setString(PARAM_NAME, arg.name);
			location.setString(PARAM_TIMEZONE, arg.timezone);
			location.setString(BAG_RELATIONS, TYPE_HOLIDAY_CALENDAR, arg.holidayCalendarId);
			tx.add(location);
			tx.commitOnClose();
		}
		return ServiceResult.success();
	}

	@Override
	public LocationArgument getArgumentInstance() {
		return new LocationArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	public static class LocationArgument extends ServiceArgument {
		public String id;
		public String name;
		public String timezone;
		public String holidayCalendarId;
	}
}
