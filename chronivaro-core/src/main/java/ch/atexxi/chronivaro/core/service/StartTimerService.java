package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.ScheduleHelper;
import ch.atexxi.chronivaro.core.model.WorkDayHelper;
import ch.atexxi.chronivaro.core.model.WorkEntryHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class StartTimerService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.value);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource employee = ChronivaroModelHelper.getEmployee(tx, arg.value);

			ZonedDateTime now = ZonedDateTime.now(ChronivaroModelHelper.getEmployeeTimezone(employee));
			String username = tx.getCertificate().getUsername();

			Resource workDay = WorkDayHelper.getOrCreateWorkDay(tx, employee, now);

			if (WorkDayHelper.findActiveWorkEntry(tx, workDay).isPresent()) {
				throw new IllegalStateException("An active work entry already exists for this employee!");
			}

			Resource workEntry = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
			workEntry.setName("Timer " + now);

			workEntry.setRelation(PARAM_EMPLOYEE, employee);
			workEntry.setRelation(PARAM_WORK_DAY, workDay);
			workEntry.setDate(PARAM_START, now);
			workEntry.setString(PARAM_SOURCE, SOURCE_TIMER);
			workEntry.setString(PARAM_CREATED_BY, username);

			Resource scheduleVersion = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE, workDay.getRelationId(PARAM_SCHEDULE), true);
			workEntry.setRelation(PARAM_SCHEDULE, scheduleVersion);

			WorkEntryHelper.validateNoOverlap(tx, employee.getId(), now, null, null);

			tx.add(workEntry);
			workDay.addRelation(PARAM_WORK_ENTRIES, workEntry);
			tx.update(workDay);

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
