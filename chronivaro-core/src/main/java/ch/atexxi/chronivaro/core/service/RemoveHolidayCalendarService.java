package ch.atexxi.chronivaro.core.service;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.model.StrolchModelConstants.BAG_RELATIONS;

public class RemoveHolidayCalendarService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource calendar = tx.getResourceBy(TYPE_HOLIDAY_CALENDAR, arg.value, true);

			// validate: no location references this calendar
			boolean locationReferencing = tx
					.streamResources(TYPE_LOCATION)
					.anyMatch(l -> l.hasParameter(BAG_RELATIONS, TYPE_HOLIDAY_CALENDAR) && l
							.getString(BAG_RELATIONS, TYPE_HOLIDAY_CALENDAR)
							.equals(arg.value));

			if (locationReferencing) {
				return ServiceResult.error("Holiday calendar is still referenced by a location!");
			}

			// cascading remove holidays
			tx
					.streamResources(TYPE_HOLIDAY)
					.filter(h -> h.hasParameter(BAG_RELATIONS, TYPE_HOLIDAY_CALENDAR) && h
							.getString(BAG_RELATIONS, TYPE_HOLIDAY_CALENDAR)
							.equals(arg.value))
					.forEach(tx::remove);

			tx.remove(calendar);
			tx.commitOnClose();
		}
		return ServiceResult.success();
	}

	@Override
	public StringArgument getArgumentInstance() {
		return new StringArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
