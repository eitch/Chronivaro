package ch.atexxi.chronivaro.core.model;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class WorkEntryHelper {

	public static Optional<Resource> findActiveWorkEntry(StrolchTransaction tx, String employeeId) {
		return tx
				.streamResources(TYPE_WORK_ENTRY)
				.filter(e -> e.getString(BAG_RELATIONS, TYPE_EMPLOYEE).equals(employeeId))
				.filter(e -> !e.hasParameter(PARAM_END) || e.getDate(PARAM_END) == null)
				.findFirst();
	}

	public static List<Resource> findWorkEntries(StrolchTransaction tx, String employeeId, ZonedDateTime from,
			ZonedDateTime to) {
		return tx
				.streamResources(TYPE_WORK_ENTRY)
				.filter(e -> e.getString(BAG_RELATIONS, TYPE_EMPLOYEE).equals(employeeId))
				.filter(e -> {
					ZonedDateTime start = e.getDate(PARAM_START);
					ZonedDateTime end = e.getDate(PARAM_END);
					if (end == null)
						end = ZonedDateTime.now(start.getZone());

					return !start.isAfter(to) && !end.isBefore(from);
				})
				.sorted((e1, e2) -> e1.getDate(PARAM_START).compareTo(e2.getDate(PARAM_START)))
				.toList();
	}

	public static void validateNoOverlap(StrolchTransaction tx, String employeeId, ZonedDateTime start,
			ZonedDateTime end, String excludeId) {
		List<Resource> existing = tx
				.streamResources(TYPE_WORK_ENTRY)
				.filter(e -> e.getString(BAG_RELATIONS, TYPE_EMPLOYEE).equals(employeeId))
				.filter(e -> !e.getId().equals(excludeId))
				.filter(e -> {
					ZonedDateTime s = e.getDate(PARAM_START);
					ZonedDateTime e1 = e.getDate(PARAM_END);
					if (e1 == null)
						e1 = ZonedDateTime.now(s.getZone());

					ZonedDateTime effectiveEnd = (end == null) ? ZonedDateTime.now(start.getZone()) : end;

					return s.isBefore(effectiveEnd) && effectiveEnd.isAfter(s) && start.isBefore(e1) && e1.isAfter(
							start);
				})
				.toList();

		if (!existing.isEmpty()) {
			throw new IllegalArgumentException("Work entry overlaps with existing entries!");
		}
	}
}
