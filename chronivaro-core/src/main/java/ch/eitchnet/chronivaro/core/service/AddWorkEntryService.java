package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.PeriodHelper;
import ch.eitchnet.chronivaro.core.model.ScheduleHelper;
import ch.eitchnet.chronivaro.core.model.WorkingLocation;
import ch.eitchnet.chronivaro.core.model.WorkDayHelper;
import ch.eitchnet.chronivaro.core.model.WorkEntryHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringResult;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResultState;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

public class AddWorkEntryService extends AbstractService<AddWorkEntryService.AddWorkEntryArgument, StringResult> {

	@Override
	protected StringResult internalDoService(AddWorkEntryArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.employeeId);
		DBC.PRE.assertNotNull("start must be set", arg.start);
		DBC.PRE.assertNotNull("end must be set", arg.end);

		String workEntryId;
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			boolean isAdminOrHr = tx.getPrivilegeContext().hasRole(ROLE_HR)
					|| tx.getPrivilegeContext().hasRole(ROLE_ADMIN)
					|| tx.getPrivilegeContext().hasRole(ROLE_ADMINISTRATOR);

			if (!isAdminOrHr) {
				ChronivaroModelHelper.assertCanManageEmployee(tx, arg.employeeId);
			}

			PeriodHelper.assertPeriodOpen(tx, arg.employeeId, arg.start.toLocalDate());
			if (arg.end.isBefore(arg.start) || arg.end.isEqual(arg.start))
				throw new IllegalArgumentException("Work entry end time must be after start time!");
			WorkEntryHelper.validateNoOverlap(tx, arg.employeeId, arg.start, arg.end, null);
			WorkEntryHelper.validateWorkingLocation(tx, arg.employeeId, arg.start, arg.end,
					arg.workingLocation == null ? null : arg.workingLocation.name(), null);

			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, arg.employeeId, true);
			Resource workDay = WorkDayHelper.getOrCreateWorkDay(tx, employee, arg.start);

			Resource workEntry = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
			workEntry.setName("WorkEntry " + arg.start);

			workEntry.setRelation(PARAM_EMPLOYEE, employee);
			workEntry.setRelation(PARAM_WORK_DAY, workDay);
			workEntry.setDate(PARAM_START, arg.start);
			workEntry.setDate(PARAM_END, arg.end);
			workEntry.setString(PARAM_SOURCE, SOURCE_MANUAL);
			workEntry.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());
			if (arg.comment != null)
				workEntry.setString(PARAM_COMMENT, arg.comment.trim());
			workEntry.setString(PARAM_WORKING_LOCATION, arg.workingLocation == null ? "" : arg.workingLocation.name());

			Resource scheduleVersion = ScheduleHelper.findScheduleVersion(tx, arg.employeeId, arg.start.toLocalDate())
					.orElseThrow(() -> new IllegalStateException("No schedule version found for employee " + arg.employeeId
							+ " on " + arg.start.toLocalDate()));
			workEntry.setRelation(PARAM_SCHEDULE, scheduleVersion);

			initVersion(workEntry, tx);
			tx.add(workEntry);
			workDay.addRelation(PARAM_WORK_ENTRIES, workEntry);
			tx.update(workDay);

			workEntryId = workEntry.getId();

			ChronivaroAuditHelper.audit(tx, TYPE_WORK_ENTRY, workEntry.getId(), AUDIT_ACTION_CREATE, arg.comment,
					"Added manual work entry for employee " + arg.employeeId + " from " + arg.start + " to " + arg.end);

			tx.commitOnClose();
		}

		return new StringResult(workEntryId);
	}

	@Override
	public AddWorkEntryArgument getArgumentInstance() {
		return new AddWorkEntryArgument();
	}

	@Override
	public StringResult getResultInstance() {
		return new StringResult(ServiceResultState.FAILED);
	}

	public static class AddWorkEntryArgument extends ServiceArgument {
		public String employeeId;
		public ZonedDateTime start;
		public ZonedDateTime end;
		public String comment;
		public WorkingLocation workingLocation;
	}
}
