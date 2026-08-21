package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.WorkDayHelper;
import ch.atexxi.chronivaro.core.model.WorkingLocation;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;

import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class ChronivaroTestHelper {

	public static Resource createEmployee(StrolchTransaction tx, String employeeId, String name) {
		return createEmployee(tx, employeeId, name, true);
	}

	public static Resource createEmployee(StrolchTransaction tx, String employeeId, String name,
			boolean createSchedule) {
		Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
		employee.setId(employeeId);
		employee.setName(name);
		employee.setBoolean(PARAM_ACTIVE, true);
		employee.setDate(PARAM_JOIN_DATE, ZonedDateTime.parse("2026-01-01T00:00:00Z"));

		if (createSchedule) {
			Resource schedule = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			schedule.setId("schedule-" + employeeId);
			schedule.setName("Schedule " + name);
			schedule.setRelation(PARAM_EMPLOYEE, employee);
			schedule.setDate(PARAM_VALID_FROM, ZonedDateTime.parse("2026-01-01T00:00:00Z"));
			schedule.setString(PARAM_WEEKLY_SCHEDULE_ID, "default-week");
			tx.add(schedule);

			employee.setRelation(PARAM_CURRENT_SCHEDULE, schedule);
		}

		tx.add(employee);
		return employee;
	}

	public static Resource createEmployee(StrolchTransaction tx, String employeeId, String name,
			ZonedDateTime joinDate) {
		return createEmployee(tx, employeeId, name, joinDate, true);
	}

	public static Resource createEmployee(StrolchTransaction tx, String employeeId, String name, ZonedDateTime joinDate,
			boolean createSchedule) {
		Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
		employee.setId(employeeId);
		employee.setName(name);
		employee.setBoolean(PARAM_ACTIVE, true);
		employee.setDate(PARAM_JOIN_DATE, joinDate);

		if (createSchedule) {
			Resource schedule = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			schedule.setId("schedule-" + employeeId);
			schedule.setName("Schedule " + name);
			schedule.setRelation(PARAM_EMPLOYEE, employee);
			schedule.setDate(PARAM_VALID_FROM, joinDate);
			schedule.setString(PARAM_WEEKLY_SCHEDULE_ID, "default-week");
			tx.add(schedule);

			employee.setRelation(PARAM_CURRENT_SCHEDULE, schedule);
		}

		tx.add(employee);
		return employee;
	}

	public static Resource createAbsenceType(StrolchTransaction tx, String code, String name) {
		Resource absenceType = tx.getResourceBy(TYPE_ABSENCE_TYPE, code, false);
		if (absenceType != null) {
			return absenceType;
		}
		absenceType = tx.getResourceTemplate(TYPE_ABSENCE_TYPE, true);
		absenceType.setId(code);
		absenceType.setName(name);
		absenceType.setString(PARAM_CODE, code);
		absenceType.setBoolean(PARAM_ACTIVE, true);
		tx.add(absenceType);
		return absenceType;
	}

	public static Resource createWorkEntry(StrolchTransaction tx, Resource employee, ZonedDateTime start,
			ZonedDateTime end) {
		Resource workDay = WorkDayHelper.getOrCreateWorkDay(tx, employee, start);
		Resource workEntry = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
		workEntry.setName("WorkEntry " + start);
		workEntry.setRelation(PARAM_EMPLOYEE, employee);
		workEntry.setRelation(PARAM_WORK_DAY, workDay);
		workEntry.setDate(PARAM_START, start);
		workEntry.setDate(PARAM_END, end);
		workEntry.setString(PARAM_WORKING_LOCATION, WorkingLocation.OFFICE);

		tx.add(workEntry);
		workDay.addRelation(PARAM_WORK_ENTRIES, workEntry);
		tx.update(workDay);

		return workEntry;
	}
}
