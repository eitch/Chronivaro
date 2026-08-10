package ch.atexxi.chronivaro.core.model;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;

import java.time.LocalDate;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class HolidayHelper {

	public static Optional<Resource> findHoliday(StrolchTransaction tx, String locationId, LocalDate date) {
		Resource location = tx.getResourceBy(TYPE_LOCATION, locationId, true);
		String holidayCalendarId = location.getString(BAG_RELATIONS, TYPE_HOLIDAY_CALENDAR);
		if (holidayCalendarId == null || holidayCalendarId.isEmpty())
			return Optional.empty();

		return tx
				.streamResources(TYPE_HOLIDAY)
				.filter(h -> h.hasParameter(BAG_RELATIONS, TYPE_HOLIDAY_CALENDAR) && h.getString(BAG_RELATIONS,
						TYPE_HOLIDAY_CALENDAR).equals(holidayCalendarId))
				.filter(h -> h.hasParameter(PARAM_DATE) && h.getDate(PARAM_DATE).toLocalDate().equals(date))
				.findFirst();
	}

	public static double getHolidayCreditFactor(StrolchTransaction tx, String locationId, LocalDate date) {
		return findHoliday(tx, locationId, date).map(h -> h.getDouble(PARAM_CREDIT_FACTOR)).orElse(0.0);
	}

	public static int getHolidayMinutes(StrolchTransaction tx, String employeeId, LocalDate date) {
		Resource employee = ChronivaroModelHelper.getEmployee(tx, employeeId);
		String locationId = employee.getString(BAG_RELATIONS, TYPE_LOCATION);
		if (locationId == null || locationId.isEmpty())
			return 0;

		double factor = getHolidayCreditFactor(tx, locationId, date);
		if (factor <= 0.0)
			return 0;

		int targetMinutes = ScheduleHelper.getTargetMinutes(tx, employeeId, date);
		return (int) Math.round(targetMinutes * factor);
	}
}
