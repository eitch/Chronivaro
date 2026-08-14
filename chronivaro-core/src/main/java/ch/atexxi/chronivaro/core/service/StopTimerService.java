package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.WorkDayHelper;
import ch.atexxi.chronivaro.core.model.WorkEntryHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class StopTimerService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.value);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource employee = ChronivaroModelHelper.getEmployee(tx, arg.value);
			ZonedDateTime now = ZonedDateTime.now(ChronivaroModelHelper.getEmployeeTimezone(employee));

			Resource workDay = WorkDayHelper.getOrCreateWorkDay(tx, employee, now);

			Optional<Resource> activeEntry = WorkDayHelper.findActiveWorkEntry(tx, workDay);
			if (activeEntry.isEmpty()) {
				throw new IllegalStateException("No active work entry found for this employee!");
			}

			Resource workEntry = activeEntry.get().getClone();

			if (now.isBefore(workEntry.getDate(PARAM_START))) {
				throw new IllegalStateException("Stop time cannot be before start time!");
			}

			workEntry.setDate(PARAM_END, now);

			WorkEntryHelper.validateNoOverlap(tx, arg.value, workEntry.getDate(PARAM_START), now, workEntry.getId());

			tx.update(workEntry);
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
