package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

public class CreateHolidayService extends AbstractService<CreateHolidayService.HolidayArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(HolidayArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("holidayCalendarId must be set", arg.holidayCalendarId);
		DBC.PRE.assertNotNull("date must be set", arg.date);
		DBC.PRE.assertNotEmpty("name must be set", arg.name);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			ZoneId zoneId = ZoneId.of("Europe/Zurich"); // Default for now
			ZonedDateTime date = arg.date.atStartOfDay(zoneId);

			boolean exists = tx
					.streamResources(TYPE_HOLIDAY)
					.filter(h -> h.hasRelation(PARAM_HOLIDAY_CALENDAR) && h
							.getRelationId(PARAM_HOLIDAY_CALENDAR)
							.equals(arg.holidayCalendarId))
					.anyMatch(h -> h.hasParameter(PARAM_DATE) && h.getDate(PARAM_DATE).equals(date));

			if (exists) {
				return ServiceResult.error("Holiday already exists for date " + arg.date);
			}

			Resource holiday = tx.getResourceTemplate(TYPE_HOLIDAY, true);
			holiday.setName(arg.name);
			holiday.setRelation(PARAM_HOLIDAY_CALENDAR,
					tx.getResourceBy(TYPE_HOLIDAY_CALENDAR, arg.holidayCalendarId, true));
			holiday.setDate(PARAM_DATE, date);
			holiday.setString(PARAM_NAME, arg.name);
			holiday.setDouble(PARAM_CREDIT_FACTOR, arg.creditFactor == 0.0 ? 1.0 : arg.creditFactor);
			initVersion(holiday, tx);
			tx.add(holiday);
			ChronivaroAuditHelper.audit(tx, TYPE_HOLIDAY, holiday.getId(), AUDIT_ACTION_CREATE,
					"Created holiday " + arg.name + " on " + arg.date);
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
		public String holidayCalendarId;
		public LocalDate date;
		public String name;
		public double creditFactor;
	}
}
