package ch.atexxi.chronivaro.core.service;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringResult;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResultState;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

public class CreateScheduleTemplateService
		extends AbstractService<CreateScheduleTemplateService.CreateScheduleTemplateArgument, StringResult> {

	@Override
	protected StringResult internalDoService(CreateScheduleTemplateArgument arg) throws Exception {
		Resource template;
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			template = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE, true);
			template.setName(arg.name);

			template.setString(PARAM_NAME, arg.name);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, arg.monday);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, arg.tuesday);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, arg.wednesday);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, arg.thursday);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, arg.friday);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, arg.saturday);
			template.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, arg.sunday);

			initVersion(template, tx);
			tx.add(template);
			tx.commitOnClose();
		}

		return new StringResult(template.getId());
	}

	@Override
	public CreateScheduleTemplateArgument getArgumentInstance() {
		return new CreateScheduleTemplateArgument();
	}

	@Override
	public StringResult getResultInstance() {
		return new StringResult(ServiceResultState.FAILED);
	}

	public static class CreateScheduleTemplateArgument extends ServiceArgument {
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
