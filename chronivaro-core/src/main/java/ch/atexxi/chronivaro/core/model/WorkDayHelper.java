package ch.atexxi.chronivaro.core.model;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.utils.dbc.DBC;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class WorkDayHelper {

	public static Resource getOrCreateWorkDay(StrolchTransaction tx, Resource employee, ZonedDateTime now) {
		LocalDate date = now.toLocalDate();
		String workDayId = employee.getRelationId(PARAM_CURRENT_WORK_DAY);

		if (workDayId != null && !workDayId.isEmpty()) {
			Resource workDay = tx.getResourceBy(TYPE_WORK_DAY, workDayId, false);
			if (workDay != null && workDay.getDate(PARAM_DATE).toLocalDate().equals(date)) {
				return workDay;
			}
		}

		// Not found or not for today, search by date and employee just in case
		Optional<Resource> existingWorkDay = tx.streamResources(TYPE_WORK_DAY)
				.filter(wd -> wd.getRelationId(PARAM_EMPLOYEE).equals(employee.getId()))
				.filter(wd -> wd.getDate(PARAM_DATE).toLocalDate().equals(date))
				.findFirst();

		if (existingWorkDay.isPresent()) {
			Resource workDay = existingWorkDay.get();
			employee.setRelation(PARAM_CURRENT_WORK_DAY, workDay);
			tx.update(employee);
			return workDay;
		}

		// Create new WorkDay
		Resource workDay = tx.getResourceTemplate(TYPE_WORK_DAY, true);
		workDay.setId(employee.getId() + "-" + date.format(DateTimeFormatter.ISO_LOCAL_DATE));
		workDay.setName("WorkDay " + employee.getName() + " " + date);
		workDay.setDate(PARAM_DATE, date.atStartOfDay(now.getZone()));
		workDay.setRelation(PARAM_EMPLOYEE, employee);

		Resource scheduleVersion = ScheduleHelper.findScheduleVersion(tx, employee.getId(), date)
				.orElseThrow(() -> new IllegalStateException("No schedule version found for employee " + employee.getId() + " on " + date));
		workDay.setRelation(PARAM_SCHEDULE, scheduleVersion);

		tx.add(workDay);

		employee.setRelation(PARAM_CURRENT_WORK_DAY, workDay);
		tx.update(employee);

		return workDay;
	}

	public static Optional<Resource> findActiveWorkEntry(StrolchTransaction tx, Resource workDay) {
		return tx.getResourcesByRelation(workDay, PARAM_WORK_ENTRIES, true).stream()
				.filter(we -> !we.hasParameter(PARAM_END) || we.getDate(PARAM_END).getYear() == 1970)
				.findFirst();
	}
}
