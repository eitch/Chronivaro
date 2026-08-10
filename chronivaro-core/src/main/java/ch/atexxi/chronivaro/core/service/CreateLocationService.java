package ch.atexxi.chronivaro.core.service;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.agent.api.StrolchAgent.getUniqueId;

public class CreateLocationService extends AbstractService<CreateLocationService.LocationArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(LocationArgument arg) throws Exception {
		String timeZone = arg.timezone == null || arg.timezone.isEmpty() ? getAgent().getTimezone() : arg.timezone;

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource location = new Resource(getUniqueId(), arg.name, TYPE_LOCATION);
			location.addParameterBag(new li.strolch.model.ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			location.addParameterBag(new li.strolch.model.ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			location.setString(PARAM_NAME, arg.name);
			location.setString(PARAM_TIMEZONE, timeZone);
			if (arg.holidayCalendarId != null)
				location.setString(BAG_RELATIONS, PARAM_HOLIDAY_CALENDAR, arg.holidayCalendarId);
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
		public String name;
		public String timezone;
		public String holidayCalendarId;
	}

	public static class UpdateLocationArgument extends LocationArgument {
		public String id;
	}
}
