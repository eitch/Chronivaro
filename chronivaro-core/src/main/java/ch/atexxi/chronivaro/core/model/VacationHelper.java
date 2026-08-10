package ch.atexxi.chronivaro.core.model;

import li.strolch.persistence.api.StrolchTransaction;

import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class VacationHelper {

	public static int getVacationBalance(StrolchTransaction tx, String employeeId, ZonedDateTime at) {
		return tx
				.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
				.filter(e -> e.getString(BAG_RELATIONS, TYPE_EMPLOYEE).equals(employeeId))
				.filter(e -> !e.getDate(PARAM_DATE).isAfter(at))
				.mapToInt(e -> e.getInteger(PARAM_VALUE))
				.sum();
	}
}
