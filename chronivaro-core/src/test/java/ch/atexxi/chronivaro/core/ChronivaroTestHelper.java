package ch.atexxi.chronivaro.core;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;

import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class ChronivaroTestHelper {

	public static Resource createEmployee(StrolchTransaction tx, String employeeId, String name) {
		return createEmployee(tx, employeeId, name, true);
	}

	public static Resource createEmployee(StrolchTransaction tx, String employeeId, String name, boolean createSchedule) {
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

	public static Resource createEmployee(StrolchTransaction tx, String employeeId, String name, ZonedDateTime joinDate) {
		return createEmployee(tx, employeeId, name, joinDate, true);
	}

	public static Resource createEmployee(StrolchTransaction tx, String employeeId, String name, ZonedDateTime joinDate, boolean createSchedule) {
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
}
