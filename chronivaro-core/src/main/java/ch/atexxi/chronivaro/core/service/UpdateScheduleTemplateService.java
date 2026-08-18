package ch.atexxi.chronivaro.core.service;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;

public class UpdateScheduleTemplateService
		extends AbstractService<UpdateScheduleTemplateService.UpdateScheduleTemplateArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(UpdateScheduleTemplateArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource template = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE, arg.id, true);
			template.setName(arg.name);

			template.setString(PARAM_NAME, arg.name);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, arg.monday);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, arg.tuesday);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, arg.wednesday);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, arg.thursday);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, arg.friday);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, arg.saturday);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, arg.sunday);

			bumpVersion(template, tx);
			tx.update(template);
			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	@Override
	public UpdateScheduleTemplateArgument getArgumentInstance() {
		return new UpdateScheduleTemplateArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	public static class UpdateScheduleTemplateArgument extends ServiceArgument {
		public String id;
		public String name;
		public int monday;
		public int tuesday;
		public int wednesday;
		public int thursday;
		public int friday;
		public int saturday;
		public int sunday;
	}
}
