package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.*;
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
import static java.text.MessageFormat.format;

public class AddWorkEntryService extends AbstractService<AddWorkEntryService.AddWorkEntryArgument, StringResult> {

	@Override
	protected StringResult internalDoService(AddWorkEntryArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.employeeId);
		DBC.PRE.assertNotNull("start must be set", arg.start);
		DBC.PRE.assertNotNull("end must be set", arg.end);

		String workEntryId;
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			boolean isAdminOrHr = tx.getPrivilegeContext().hasRole(ROLE_HR) || tx
					.getPrivilegeContext()
					.hasRole(ROLE_ADMIN) || tx.getPrivilegeContext().hasRole(ROLE_ADMINISTRATOR);

			if (!isAdminOrHr) {
				ChronivaroModelHelper.assertCanManageEmployee(tx, arg.employeeId);
			}

			if (arg.end.isBefore(arg.start) || arg.end.isEqual(arg.start))
				throw new IllegalArgumentException("Work entry end time must be after start time!");

			boolean spansMidnight = arg.end.toLocalDate().equals(arg.start.toLocalDate().plusDays(1));
			if (!arg.start.toLocalDate().equals(arg.end.toLocalDate()) && !spansMidnight) {
				throw new IllegalArgumentException(
						"Work entry must start and end on the same day or end on the next day!");
			}

			PeriodHelper.assertPeriodOpen(tx, arg.employeeId, arg.start.toLocalDate());
			if (spansMidnight) {
				PeriodHelper.assertPeriodOpen(tx, arg.employeeId, arg.end.toLocalDate());
			}

			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, arg.employeeId, true);

			if (spansMidnight) {
				ZonedDateTime midnight = arg.start.toLocalDate().plusDays(1).atStartOfDay(arg.start.getZone());

				WorkEntryHelper.validateNoOverlap(tx, arg.employeeId, arg.start, midnight, null);
				WorkEntryHelper.validateWorkingLocation(tx, arg.employeeId, arg.start, midnight,
						arg.workingLocation == null ? null : arg.workingLocation.name(), null);

				WorkEntryHelper.validateNoOverlap(tx, arg.employeeId, midnight, arg.end, null);
				WorkEntryHelper.validateWorkingLocation(tx, arg.employeeId, midnight, arg.end,
						arg.workingLocation == null ? null : arg.workingLocation.name(), null);

				// Entry 1 on start day
				Resource workDay1 = WorkDayHelper.getOrCreateWorkDay(tx, employee, arg.start);
				Resource workEntry1 = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
				workEntry1.setName("WorkEntry " + arg.start);
				workEntry1.setRelation(PARAM_EMPLOYEE, employee);
				workEntry1.setRelation(PARAM_WORK_DAY, workDay1);
				workEntry1.setDate(PARAM_START, arg.start);
				workEntry1.setDate(PARAM_END, midnight);
				workEntry1.setString(PARAM_SOURCE, SOURCE_MANUAL);
				workEntry1.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());
				if (arg.comment != null)
					workEntry1.setString(PARAM_COMMENT, arg.comment.trim());
				workEntry1.setString(PARAM_WORKING_LOCATION,
						arg.workingLocation == null ? "" : arg.workingLocation.name());
				workEntry1.setBoolean(PARAM_IS_ON_CALL, Boolean.TRUE.equals(arg.isOnCall));

				Resource scheduleVersion1 = ScheduleHelper
						.findScheduleVersion(tx, arg.employeeId, arg.start.toLocalDate())
						.orElseThrow(() -> new IllegalStateException("No schedule version found for employee "
								+ arg.employeeId
								+ " on "
								+ arg.start.toLocalDate()));
				workEntry1.setRelation(PARAM_SCHEDULE, scheduleVersion1);

				initVersion(workEntry1, tx);
				tx.add(workEntry1);
				workDay1.addRelation(PARAM_WORK_ENTRIES, workEntry1);
				tx.update(workDay1);

				workEntryId = workEntry1.getId();

				ChronivaroAuditHelper.audit(tx, TYPE_WORK_ENTRY, workEntry1.getId(), AUDIT_ACTION_CREATE, arg.comment,
						format("Added manual work entry for employee {0} from {1} to {2}",
								employee.getString(PARAM_PERSONAL_NUMBER), arg.start, midnight));

				// Entry 2 on next day
				Resource workDay2 = WorkDayHelper.getOrCreateWorkDay(tx, employee, arg.end);
				Resource workEntry2 = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
				workEntry2.setName("WorkEntry " + midnight);
				workEntry2.setRelation(PARAM_EMPLOYEE, employee);
				workEntry2.setRelation(PARAM_WORK_DAY, workDay2);
				workEntry2.setDate(PARAM_START, midnight);
				workEntry2.setDate(PARAM_END, arg.end);
				workEntry2.setString(PARAM_SOURCE, SOURCE_MANUAL);
				workEntry2.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());
				if (arg.comment != null)
					workEntry2.setString(PARAM_COMMENT, arg.comment.trim());
				workEntry2.setString(PARAM_WORKING_LOCATION,
						arg.workingLocation == null ? "" : arg.workingLocation.name());
				workEntry2.setBoolean(PARAM_IS_ON_CALL, Boolean.TRUE.equals(arg.isOnCall));

				Resource scheduleVersion2 = ScheduleHelper
						.findScheduleVersion(tx, arg.employeeId, arg.end.toLocalDate())
						.orElseThrow(() -> new IllegalStateException(
								format("No schedule version found for employee {0} on {1}",
										employee.getString(PARAM_PERSONAL_NUMBER), arg.end.toLocalDate())));
				workEntry2.setRelation(PARAM_SCHEDULE, scheduleVersion2);

				initVersion(workEntry2, tx);
				tx.add(workEntry2);
				workDay2.addRelation(PARAM_WORK_ENTRIES, workEntry2);
				tx.update(workDay2);

				ChronivaroAuditHelper.audit(tx, TYPE_WORK_ENTRY, workEntry2.getId(), AUDIT_ACTION_CREATE, arg.comment,
						format("Added split manual work entry for employee {0} from {1} to {2}",
								employee.getString(PARAM_PERSONAL_NUMBER), midnight, arg.end));

			} else {
				WorkEntryHelper.validateNoOverlap(tx, arg.employeeId, arg.start, arg.end, null);
				WorkEntryHelper.validateWorkingLocation(tx, arg.employeeId, arg.start, arg.end,
						arg.workingLocation == null ? null : arg.workingLocation.name(), null);

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
				workEntry.setString(PARAM_WORKING_LOCATION,
						arg.workingLocation == null ? "" : arg.workingLocation.name());
				workEntry.setBoolean(PARAM_IS_ON_CALL, Boolean.TRUE.equals(arg.isOnCall));

				Resource scheduleVersion = ScheduleHelper
						.findScheduleVersion(tx, arg.employeeId, arg.start.toLocalDate())
						.orElseThrow(() -> new IllegalStateException(
								format("No schedule version found for employee {0} on {1}",
										employee.getString(PARAM_PERSONAL_NUMBER), arg.start.toLocalDate())));
				workEntry.setRelation(PARAM_SCHEDULE, scheduleVersion);

				initVersion(workEntry, tx);
				tx.add(workEntry);
				workDay.addRelation(PARAM_WORK_ENTRIES, workEntry);
				tx.update(workDay);

				workEntryId = workEntry.getId();

				ChronivaroAuditHelper.audit(tx, TYPE_WORK_ENTRY, workEntry.getId(), AUDIT_ACTION_CREATE, arg.comment,
						format("Added manual work entry for employee {0} from {1} to {2}",
								employee.getString(PARAM_PERSONAL_NUMBER), arg.start, arg.end));
			}

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
		public Boolean isOnCall;
	}
}
