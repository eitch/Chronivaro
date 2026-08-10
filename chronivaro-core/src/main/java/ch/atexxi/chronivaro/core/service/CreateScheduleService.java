package ch.atexxi.chronivaro.core.service;

import li.strolch.model.ParameterBag;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.agent.api.StrolchAgent.getUniqueId;

public class CreateScheduleService
		extends AbstractService<CreateScheduleService.CreateScheduleArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(CreateScheduleArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource schedule = new Resource(getUniqueId(), "Schedule for " + arg.employeeId,
					TYPE_EMPLOYMENT_SCHEDULE_VERSION);
			schedule.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			schedule.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));

			schedule.setString(BAG_RELATIONS, TYPE_EMPLOYEE, arg.employeeId);
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

			tx.add(schedule);
			tx.commitOnClose();
		}

		return ServiceResult.success();
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
