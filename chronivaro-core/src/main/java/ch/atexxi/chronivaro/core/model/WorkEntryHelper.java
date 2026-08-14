package ch.atexxi.chronivaro.core.model;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static java.util.Comparator.comparing;

public class WorkEntryHelper {

	public static Optional<Resource> findActiveWorkEntry(StrolchTransaction tx, String employeeId) {
		Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
		String workDayId = employee.getRelationId(PARAM_CURRENT_WORK_DAY);
		if (workDayId != null && !workDayId.isEmpty()) {
			Resource workDay = tx.getResourceBy(TYPE_WORK_DAY, workDayId, false);
			if (workDay != null) {
				Optional<Resource> activeEntry = WorkDayHelper.findActiveWorkEntry(tx, workDay);
				if (activeEntry.isPresent())
					return activeEntry;
			}
		}

		return tx
				.streamResources(TYPE_WORK_ENTRY)
				.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(e -> !e.hasParameter(PARAM_END) || e.getDate(PARAM_END).getYear() == 1970)
				.findFirst();
	}

	public static List<Resource> findWorkEntries(StrolchTransaction tx, String employeeId, ZonedDateTime from,
			ZonedDateTime to) {

		// We need to look at WorkDays in the range [from - 1 day, to]
		LocalDate fromDate = from.toLocalDate().minusDays(1);
		LocalDate toDate = to.toLocalDate();

		List<Resource> workDays = tx
				.streamResources(TYPE_WORK_DAY)
				.filter(wd -> wd.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(wd -> {
					LocalDate wdDate = wd.getDate(PARAM_DATE).toLocalDate();
					return (wdDate.isEqual(fromDate) || wdDate.isAfter(fromDate)) && (
							wdDate.isEqual(toDate) || wdDate.isBefore(toDate));
				})
				.toList();

		return workDays
				.stream()
				.flatMap(wd -> tx.getResourcesByRelation(wd, PARAM_WORK_ENTRIES, true).stream().filter(we -> {
					ZonedDateTime start = we.getDate(PARAM_START);
					ZonedDateTime end = we.getDate(PARAM_END);
					if (end.getYear() == 1970)
						end = ZonedDateTime.now(start.getZone());

					return !start.isAfter(to) && !end.isBefore(from);
				}))
				.distinct()
				.sorted(comparing(we -> we.getDate(PARAM_START)))
				.toList();
	}

	public static void validateNoOverlap(StrolchTransaction tx, String employeeId, ZonedDateTime start,
			ZonedDateTime end, String excludeId) {

		if (end != null && end.getYear() != 1970 && !start.toLocalDate().equals(end.toLocalDate())) {
			throw new IllegalArgumentException("Work entry cannot span multiple days!");
		}

		// Overlap validation needs to check current day and previous day for shifts spanning midnight
		LocalDate fromDate = start.toLocalDate().minusDays(1);
		LocalDate toDate = (end == null) ? start.toLocalDate() : end.toLocalDate();

		List<Resource> workDays = tx
				.streamResources(TYPE_WORK_DAY)
				.filter(wd -> wd.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(wd -> {
					LocalDate wdDate = wd.getDate(PARAM_DATE).toLocalDate();
					return (wdDate.isEqual(fromDate) || wdDate.isAfter(fromDate)) && (
							wdDate.isEqual(toDate) || wdDate.isBefore(toDate));
				})
				.toList();

		ZonedDateTime effectiveEnd = (end == null || end.getYear() == 1970) ? ZonedDateTime.now(start.getZone()) : end;

		List<Resource> existing = workDays
				.stream()
				.flatMap(wd -> tx.getResourcesByRelation(wd, PARAM_WORK_ENTRIES, true).stream())
				.distinct()
				.filter(e -> !e.getId().equals(excludeId))
				.filter(e -> {
					ZonedDateTime s = e.getDate(PARAM_START);
					ZonedDateTime e1 = e.getDate(PARAM_END);
					if (e1.getYear() == 1970)
						e1 = ZonedDateTime.now(s.getZone());

					return s.isBefore(effectiveEnd) && e1.isAfter(start);
				})
				.toList();

		if (!existing.isEmpty()) {
			throw new IllegalArgumentException("Work entry overlaps with existing entries!");
		}
	}
}
