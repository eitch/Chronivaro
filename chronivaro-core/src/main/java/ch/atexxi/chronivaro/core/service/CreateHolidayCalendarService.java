package ch.atexxi.chronivaro.core.service;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.ServiceResult;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class CreateHolidayCalendarService extends AbstractService<CreateHolidayCalendarService.HolidayCalendarArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(HolidayCalendarArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource calendar = new Resource(arg.id, arg.name, TYPE_HOLIDAY_CALENDAR);
			calendar.addParameterBag(new li.strolch.model.ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			calendar.setString(PARAM_NAME, arg.name);
			tx.add(calendar);
			tx.commitOnClose();
		}
		return ServiceResult.success();
	}

	@Override
	public HolidayCalendarArgument getArgumentInstance() {
		return new HolidayCalendarArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	public static class HolidayCalendarArgument extends ServiceArgument {
		public String id;
		public String name;
	}
}
