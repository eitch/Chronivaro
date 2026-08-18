package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.WorkDayHelper;
import ch.atexxi.chronivaro.core.model.WorkEntryHelper;
import ch.atexxi.chronivaro.core.model.WorkingLocation;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class StartTimerService extends AbstractService<StartTimerService.Argument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(Argument arg) throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.employeeId);
		if (!arg.workingLocation.name().isEmpty())
			WorkingLocation.valueOf(arg.workingLocation.name());

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource employee = ChronivaroModelHelper.getEmployee(tx, arg.employeeId);

			ZonedDateTime now = ZonedDateTime.now(ChronivaroModelHelper.getEmployeeTimezone(employee));
			String username = tx.getCertificate().getUsername();

			Resource workDay = WorkDayHelper.getOrCreateWorkDay(tx, employee, now);

			if (WorkEntryHelper.findActiveWorkEntry(tx, arg.employeeId).isPresent()) {
				throw new IllegalStateException("An active work entry already exists for this employee!");
			}

			Resource workEntry = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
			workEntry.setName("Timer " + now);

			workEntry.setRelation(PARAM_EMPLOYEE, employee);
			workEntry.setRelation(PARAM_WORK_DAY, workDay);
			workEntry.setDate(PARAM_START, now);
			workEntry.setString(PARAM_SOURCE, SOURCE_TIMER);
			workEntry.setString(PARAM_CREATED_BY, username);
			workEntry.setString(PARAM_WORKING_LOCATION, arg.workingLocation);

			Resource scheduleVersion = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE, workDay.getRelationId(PARAM_SCHEDULE),
					true);
			workEntry.setRelation(PARAM_SCHEDULE, scheduleVersion);

			WorkEntryHelper.validateNoOverlap(tx, employee.getId(), now, null, null);

			tx.add(workEntry);
			workDay.addRelation(PARAM_WORK_ENTRIES, workEntry);
			tx.update(workDay);

			ChronivaroAuditHelper.audit(tx, TYPE_WORK_ENTRY, workEntry.getId(), AUDIT_ACTION_START,
					"Started timer for employee " + employee.getId() + " at " + now);

			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	@Override
	public Argument getArgumentInstance() {
		return new Argument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	public static class Argument extends ServiceArgument {
		public String employeeId;
		public WorkingLocation workingLocation;

		public Argument() {

		}

		public Argument(String employeeId, WorkingLocation workingLocation) {
			this.employeeId = employeeId;
			this.workingLocation = workingLocation;
		}
	}
}
