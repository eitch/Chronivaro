package ch.atexxi.chronivaro.core.service;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.ServiceResult;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;

import java.time.LocalDate;
import java.time.ZoneId;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class CreateHolidayService extends AbstractService<CreateHolidayService.HolidayArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(HolidayArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			ZoneId zoneId = ZoneId.of("Europe/Zurich"); // Default for now

			Resource holiday = new Resource(arg.id, arg.name, TYPE_HOLIDAY);
			holiday.addParameterBag(new li.strolch.model.ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			holiday.addParameterBag(new li.strolch.model.ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			holiday.setString(BAG_RELATIONS, TYPE_HOLIDAY_CALENDAR, arg.holidayCalendarId);
			holiday.setDate(PARAM_DATE, arg.date.atStartOfDay(zoneId));
			holiday.setString(PARAM_NAME, arg.name);
			holiday.setDouble(PARAM_CREDIT_FACTOR, arg.creditFactor);
			tx.add(holiday);
			tx.commitOnClose();
		}
		return ServiceResult.success();
	}

	@Override
	public HolidayArgument getArgumentInstance() {
		return new HolidayArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	public static class HolidayArgument extends ServiceArgument {
		public String id;
		public String holidayCalendarId;
		public LocalDate date;
		public String name;
		public double creditFactor;
	}
}
