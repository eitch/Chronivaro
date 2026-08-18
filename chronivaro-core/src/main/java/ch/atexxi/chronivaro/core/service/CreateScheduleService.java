package ch.atexxi.chronivaro.core.service;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.initVersion;
import static java.text.MessageFormat.format;

public class CreateScheduleService
		extends AbstractService<CreateScheduleService.CreateScheduleArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(CreateScheduleArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			validateNoOverlap(tx, arg);

			Resource schedule = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
			schedule.setName("Schedule for " + arg.employeeId);

			schedule.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, arg.employeeId, true));
			schedule.setDate(PARAM_VALID_FROM, arg.validFrom);
			if (arg.validTo != null)
				schedule.setDate(PARAM_VALID_TO, arg.validTo);

			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, arg.monday);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, arg.tuesday);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, arg.wednesday);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, arg.thursday);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, arg.friday);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, arg.saturday);
			schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, arg.sunday);

			initVersion(schedule, tx);
			tx.add(schedule);
			updateEmployeeCurrentSchedule(tx, arg.employeeId, schedule, arg.validFrom, arg.validTo);

			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	private void validateNoOverlap(StrolchTransaction tx, CreateScheduleArgument arg) {
		tx
				.streamResources(TYPE_EMPLOYMENT_SCHEDULE)
				.filter(r -> r.getRelationId(PARAM_EMPLOYEE).equals(arg.employeeId))
				.forEach(r -> {
					ZonedDateTime from = r.getDate(PARAM_VALID_FROM);
					ZonedDateTime to = r.hasParameter(PARAM_VALID_TO) ? r.getDate(PARAM_VALID_TO) : null;

					// Overlap if:
					// arg.from <= r.to AND arg.to >= r.from
					// (null 'to' means infinity)

					boolean overlaps = true;
					if (to != null && arg.validFrom.isAfter(to))
						overlaps = false;
					if (arg.validTo != null && arg.validTo.isBefore(from))
						overlaps = false;

					if (overlaps) {
						throw new IllegalArgumentException(
								format("New schedule version overlaps with existing version {0} ({1} - {2})", r.getId(),
										from, to == null ? "open" : to));
					}
				});
	}

	private void updateEmployeeCurrentSchedule(StrolchTransaction tx, String employeeId, Resource schedule,
			ZonedDateTime validFrom, ZonedDateTime validTo) {

		ZonedDateTime now = ZonedDateTime.now();
		if (now.isBefore(validFrom))
			return;
		if (validTo != null && now.isAfter(validTo))
			return;

		Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
		employee.setRelation(PARAM_CURRENT_SCHEDULE, schedule);
		tx.update(employee);
	}

	@Override
	public CreateScheduleArgument getArgumentInstance() {
		return new CreateScheduleArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	public static class CreateScheduleArgument extends ServiceArgument {
		public String employeeId;
		public ZonedDateTime validFrom;
		public ZonedDateTime validTo;
		public int monday;
		public int tuesday;
		public int wednesday;
		public int thursday;
		public int friday;
		public int saturday;
		public int sunday;
	}
}
