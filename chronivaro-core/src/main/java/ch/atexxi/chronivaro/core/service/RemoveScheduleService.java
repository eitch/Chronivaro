package ch.atexxi.chronivaro.core.service;

import li.strolch.exception.StrolchUserMessageException;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.I18nMessage;

import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class RemoveScheduleService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource schedule = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE_VERSION, arg.value, true);

			String employeeId = schedule.getRelationId(PARAM_EMPLOYEE);
			ZonedDateTime validFrom = schedule.getDate(PARAM_VALID_FROM);
			ZonedDateTime validTo = schedule.getDate(PARAM_VALID_TO);

			long workEntries = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.filter(e -> {
						ZonedDateTime start = e.getDate(PARAM_START);
						if (start.isBefore(validFrom))
							return false;
						return validTo == null || !start.isAfter(validTo);
					})
					.count();

			if (workEntries > 0) {
				throw new StrolchUserMessageException(
						new I18nMessage("chronivaro", "chronivaro.schedule.delete.fail.workentries", null,
								"Cannot delete schedule because it has " + workEntries
										+ " work entries associated with it. Please update the schedule instead.")
								.value("count", workEntries));
			}

			tx.remove(schedule);
			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	@Override
	public StringArgument getArgumentInstance() {
		return new StringArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
