package ch.atexxi.chronivaro.core.service;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.StringResult;
import li.strolch.service.api.ServiceResultState;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.agent.api.StrolchAgent.getUniqueId;

public class CreateHolidayCalendarService
		extends AbstractService<CreateHolidayCalendarService.HolidayCalendarArgument, StringResult> {

	@Override
	protected StringResult internalDoService(HolidayCalendarArgument arg) throws Exception {
		String id = getUniqueId();
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource calendar = new Resource(id, arg.name, TYPE_HOLIDAY_CALENDAR);
			calendar.addParameterBag(new li.strolch.model.ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			calendar.setString(PARAM_NAME, arg.name);
			tx.add(calendar);
			tx.commitOnClose();
		}
		return new StringResult(id);
	}

	@Override
	public HolidayCalendarArgument getArgumentInstance() {
		return new HolidayCalendarArgument();
	}

	@Override
	public StringResult getResultInstance() {
		return new StringResult(ServiceResultState.FAILED);
	}

	public static class HolidayCalendarArgument extends ServiceArgument {
		public String name;
	}
}
