package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.VacationHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.TreeSet;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

public class UpdateScheduleService
		extends AbstractService<UpdateScheduleService.UpdateScheduleArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(UpdateScheduleArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg).rollbackOnFailure()) {
			Resource schedule = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE, arg.id, true);

			String employeeId = schedule.getRelationId(PARAM_EMPLOYEE);
			ZonedDateTime oldValidFrom = schedule.getDate(PARAM_VALID_FROM);

			boolean hasWorkEntries = hasWorkEntries(tx, employeeId, schedule);

			if (hasWorkEntries || !arg.validFrom.equals(oldValidFrom)) {
				// We must version the schedule
				// If new validFrom is after old validFrom, we close the old one
				if (arg.validFrom.isAfter(oldValidFrom)) {
					schedule.setDate(PARAM_VALID_TO,
							arg.validFrom.minusDays(1).withHour(23).withMinute(59).withSecond(59));
					bumpVersion(schedule, tx);
					tx.update(schedule);
					ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYMENT_SCHEDULE, schedule.getId(), AUDIT_ACTION_UPDATE,
							"Closed previous schedule version " + schedule.getId() + " validTo=" + schedule.getDate(PARAM_VALID_TO));

					// Create new version
					Resource newVersion = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
					newVersion.setName("Schedule for " + employeeId);
					newVersion.setRelationId(PARAM_EMPLOYEE, employeeId);
					updateSchedule(newVersion, arg);
					initVersion(newVersion, tx);
					tx.add(newVersion);
					ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYMENT_SCHEDULE, newVersion.getId(), AUDIT_ACTION_CREATE,
							"Created new schedule version for employee " + employeeId + " validFrom=" + arg.validFrom
									+ (arg.validTo != null ? " to " + arg.validTo : ""));
				} else {
					// If new validFrom is before or same, we just update the existing one if no work entries,
					// but here we know hasWorkEntries is true or validFrom changed.
					// If validFrom changed to earlier, we might have overlapping versions.
					// For now, let's keep it simple: if validFrom changed, we update the existing one
					// but only if it doesn't conflict.
					// Actually, the user's requirement is to keep history.
					// If the user wants to change a schedule that already has work entries, they should probably
					// create a new one anyway.

					// Let's stick to: if hasWorkEntries, then we SHOULD NOT change the existing record's values that affect those work entries.
					if (hasWorkEntries && !isSameTargetTime(schedule, arg)) {
						// If target times changed, we MUST version.
						// If the user wants to change it from the beginning, but work entries exist,
						// we should probably forbid it or force them to pick a new start date.
						if (arg.validFrom.equals(oldValidFrom)) {
							throw new RuntimeException(
									"Cannot change target times of an existing schedule that has work entries. Please create a new schedule version with a later start date.");
						}
					}

					updateSchedule(schedule, arg);
					bumpVersion(schedule, tx);
					tx.update(schedule);
					ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYMENT_SCHEDULE, schedule.getId(), AUDIT_ACTION_UPDATE,
							"Updated schedule " + schedule.getId() + " for employee " + employeeId);
				}
			} else {
				// No work entries and same validFrom, just update
				updateSchedule(schedule, arg);
				bumpVersion(schedule, tx);
				tx.update(schedule);
				ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYMENT_SCHEDULE, schedule.getId(), AUDIT_ACTION_UPDATE,
						"Updated schedule " + schedule.getId() + " for employee " + employeeId);
			}

			updateEmployeeCurrentSchedule(tx, employeeId);
			tx.commitOnClose();
		}

		try (StrolchTransaction tx = openArgOrUserTx(arg).rollbackOnFailure()) {
			Resource schedule = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE, arg.id, true);
			String employeeId = schedule.getRelationId(PARAM_EMPLOYEE);

			Set<Integer> years = new TreeSet<>();
			years.add(arg.validFrom.getYear());
			if (arg.validTo != null) {
				years.add(arg.validTo.getYear());
			}
			tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
					.filter(e -> e.hasRelation(PARAM_EMPLOYEE) && employeeId.equals(e.getRelationId(PARAM_EMPLOYEE))
							&& VACATION_ENTITLEMENT.equals(e.getString(PARAM_VACATION_TYPE)))
					.map(e -> e.getDate(PARAM_DATE).getYear())
					.forEach(years::add);

			for (int year : years) {
				VacationHelper.creditOrRecalculateEntitlement(tx, employeeId, year, true);
			}

			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	private void updateEmployeeCurrentSchedule(StrolchTransaction tx, String employeeId) {
		ZonedDateTime now = ZonedDateTime.now();
		Resource schedule = tx
				.streamResources(TYPE_EMPLOYMENT_SCHEDULE)
				.filter(s -> s.hasRelation(PARAM_EMPLOYEE) && employeeId.equals(s.getRelationId(PARAM_EMPLOYEE)))
				.filter(s -> {
					ZonedDateTime validFrom = s.getDate(PARAM_VALID_FROM);
					ZonedDateTime validTo = s.hasParameter(PARAM_VALID_TO) ? s.getDate(PARAM_VALID_TO) : null;
					return !now.isBefore(validFrom) && (validTo == null || !now.isAfter(validTo));
				})
				.findFirst()
				.orElse(null);

		Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
		if (schedule == null) {
			employee.removeParameter(BAG_RELATIONS, PARAM_CURRENT_SCHEDULE);
		} else {
			employee.setRelation(PARAM_CURRENT_SCHEDULE, schedule);
		}
		tx.update(employee);
	}

	private boolean isSameTargetTime(Resource schedule, UpdateScheduleArgument arg) {
		return schedule.getInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY) == arg.monday
				&& schedule.getInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY) == arg.tuesday
				&& schedule.getInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY) == arg.wednesday
				&& schedule.getInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY) == arg.thursday
				&& schedule.getInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY) == arg.friday
				&& schedule.getInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY) == arg.saturday
				&& schedule.getInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY) == arg.sunday;
	}

	private void updateSchedule(Resource schedule, UpdateScheduleArgument arg) {
		schedule.setDate(PARAM_VALID_FROM, arg.validFrom);
		if (arg.validTo != null)
			schedule.setDate(PARAM_VALID_TO, arg.validTo);
		else
			schedule.removeParameter(BAG_PARAMETERS, PARAM_VALID_TO);

		schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, arg.monday);
		schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, arg.tuesday);
		schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, arg.wednesday);
		schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, arg.thursday);
		schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, arg.friday);
		schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, arg.saturday);
		schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, arg.sunday);

		int weeklyMinutes = arg.monday + arg.tuesday + arg.wednesday + arg.thursday + arg.friday + arg.saturday + arg.sunday;
		schedule.setInteger(PARAM_WEEKLY_TARGET_MINUTES, weeklyMinutes);
		if (arg.employmentRate != null) {
			schedule.setDouble(PARAM_EMPLOYMENT_RATE, arg.employmentRate);
		} else {
			schedule.setDouble(PARAM_EMPLOYMENT_RATE, (double) weeklyMinutes / (5.0 * DEFAULT_MINUTES_PER_VACATION_DAY));
		}
	}

	private boolean hasWorkEntries(StrolchTransaction tx, String employeeId, Resource schedule) {
		ZonedDateTime validFrom = schedule.getDate(PARAM_VALID_FROM);
		ZonedDateTime validTo = schedule.hasParameter(PARAM_VALID_TO) ? schedule.getDate(PARAM_VALID_TO) : null;

		return tx
				.streamResources(TYPE_WORK_ENTRY)
				.filter(e -> e.hasRelation(PARAM_EMPLOYEE) && employeeId.equals(e.getRelationId(PARAM_EMPLOYEE)))
				.anyMatch(e -> {
					ZonedDateTime start = e.getDate(PARAM_START);
					if (start.isBefore(validFrom))
						return false;
					return validTo == null || !start.isAfter(validTo);
				});
	}

	@Override
	public UpdateScheduleArgument getArgumentInstance() {
		return new UpdateScheduleArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	public static class UpdateScheduleArgument extends ServiceArgument {
		public String id;
		public ZonedDateTime validFrom;
		public ZonedDateTime validTo;
		public Double employmentRate;
		public int monday;
		public int tuesday;
		public int wednesday;
		public int thursday;
		public int friday;
		public int saturday;
		public int sunday;
	}
}
