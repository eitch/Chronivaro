package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringResult;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResultState;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

public class CreateHolidayCalendarService
		extends AbstractService<CreateHolidayCalendarService.HolidayCalendarArgument, StringResult> {

	@Override
	protected StringResult internalDoService(HolidayCalendarArgument arg) throws Exception {
		Resource calendar;
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			calendar = tx.getResourceTemplate(TYPE_HOLIDAY_CALENDAR, true);
			calendar.setName(arg.name);
			calendar.setString(PARAM_NAME, arg.name);
			initVersion(calendar, tx);
			tx.add(calendar);
			ChronivaroAuditHelper.audit(tx, TYPE_HOLIDAY_CALENDAR, calendar.getId(), AUDIT_ACTION_CREATE,
					"Created holiday calendar " + arg.name);
			tx.commitOnClose();
		}
		return new StringResult(calendar.getId());
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
