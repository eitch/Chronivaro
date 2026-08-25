package ch.eitchnet.chronivaro.core.model;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.utils.dbc.DBC;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;

public class WorkDayHelper {

	public static Resource getOrCreateWorkDay(StrolchTransaction tx, Resource employee, ZonedDateTime now) {
		LocalDate date = now.toLocalDate();
		LocalDate today = LocalDate.now(now.getZone());
		String expectedWorkDayId = employee.getId() + "-" + date.format(DateTimeFormatter.ISO_LOCAL_DATE);

		Resource existingWorkDay = tx.getResourceBy(TYPE_WORK_DAY, expectedWorkDayId, false);
		if (existingWorkDay != null) {
			if (date.equals(today) && !expectedWorkDayId.equals(employee.getRelationId(PARAM_CURRENT_WORK_DAY))) {
				Resource employeeClone = tx.readLock(employee);
				employeeClone.setRelation(PARAM_CURRENT_WORK_DAY, existingWorkDay);
				tx.update(employeeClone);
			}
			return tx.readLock(existingWorkDay);
		}

		// Create new WorkDay
		Resource workDay = tx.getResourceTemplate(TYPE_WORK_DAY, true);
		workDay.setId(expectedWorkDayId);
		workDay.setName("WorkDay " + employee.getName() + " " + date);
		workDay.setDate(PARAM_DATE, date.atStartOfDay(now.getZone()));
		workDay.setRelation(PARAM_EMPLOYEE, employee);

		Resource scheduleVersion = ScheduleHelper.findScheduleVersion(tx, employee.getId(), date)
				.orElseThrow(() -> new IllegalStateException("No schedule version found for employee " + employee.getId() + " on " + date));
		workDay.setRelation(PARAM_SCHEDULE, scheduleVersion);

		tx.add(workDay);

		if (date.equals(today)) {
			Resource employeeClone = tx.readLock(employee);
			employeeClone.setRelation(PARAM_CURRENT_WORK_DAY, workDay);
			tx.update(employeeClone);
		}

		return workDay;
	}

	public static Optional<Resource> findActiveWorkEntry(StrolchTransaction tx, Resource workDay) {
		return tx.getResourcesByRelation(workDay, PARAM_WORK_ENTRIES, true).stream()
				.filter(we -> !we.hasParameter(PARAM_END) || we.getDate(PARAM_END).getYear() == 1970)
				.findFirst();
	}
}
