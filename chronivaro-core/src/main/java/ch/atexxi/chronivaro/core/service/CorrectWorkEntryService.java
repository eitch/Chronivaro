package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.PeriodHelper;
import ch.atexxi.chronivaro.core.model.ScheduleHelper;
import ch.atexxi.chronivaro.core.model.WorkDayHelper;
import ch.atexxi.chronivaro.core.model.WorkEntryHelper;
import ch.atexxi.chronivaro.core.model.WorkingLocation;
import li.strolch.exception.StrolchModelException;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;

public class CorrectWorkEntryService
		extends AbstractService<CorrectWorkEntryService.CorrectWorkEntryArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(CorrectWorkEntryArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("workEntryId must be set", arg.workEntryId);
		DBC.PRE.assertNotNull("start must be set", arg.start);
		DBC.PRE.assertNotNull("end must be set", arg.end);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource workEntry = tx.getResourceBy(TYPE_WORK_ENTRY, arg.workEntryId, true).getClone();
			String employeeId = workEntry.getRelationId(PARAM_EMPLOYEE);

			ZonedDateTime oldStart = workEntry.getDate(PARAM_START);
			ZonedDateTime oldEnd = workEntry.getDate(PARAM_END);
			if (oldEnd != null && oldEnd.getYear() == 1970)
				oldEnd = null;

			PeriodHelper.assertPeriodOpen(tx, employeeId, oldStart.toLocalDate());
			if (!arg.start.toLocalDate().equals(oldStart.toLocalDate())) {
				PeriodHelper.assertPeriodOpen(tx, employeeId, arg.start.toLocalDate());
			}

			if (arg.end.isBefore(arg.start) || arg.end.isEqual(arg.start)) {
				throw new IllegalArgumentException("Work entry end time must be after start time!");
			}

			boolean isAdminOrHr = tx.getPrivilegeContext().hasRole(ROLE_HR)
					|| tx.getPrivilegeContext().hasRole(ROLE_ADMIN)
					|| tx.getPrivilegeContext().hasRole(ROLE_ADMINISTRATOR);

			if (!isAdminOrHr) {
				Optional<Resource> callerEmployee = ChronivaroModelHelper.findEmployeeByUser(tx, tx.getCertificate().getUserId());
				boolean isSelf = callerEmployee.isPresent() && callerEmployee.get().getId().equals(employeeId);

				if (isSelf) {
					// Regular employee self-service restrictions:
					// 1. Cannot change start time
					if (!arg.start.isEqual(oldStart)) {
						throw new StrolchModelException("Employees are only permitted to shorten work entries and cannot modify the start time.");
					}
					// 2. Cannot extend end time
					if (oldEnd != null && arg.end.isAfter(oldEnd)) {
						throw new StrolchModelException("Employees can only shorten work entries to an earlier time of day, not extend them.");
					}
					if (oldEnd == null) {
						ZonedDateTime now = ZonedDateTime.now(ChronivaroModelHelper.getEmployeeTimezone(callerEmployee.get()));
						if (arg.end.isAfter(now)) {
							throw new StrolchModelException("End time cannot be in the future.");
						}
					}
				} else {
					// Not self, check supervisor permission
					ChronivaroModelHelper.assertCanManageEmployee(tx, employeeId);
				}
			}

			WorkEntryHelper.validateNoOverlap(tx, employeeId, arg.start, arg.end, workEntry.getId());
			WorkEntryHelper.validateWorkingLocation(tx, employeeId, arg.start, arg.end,
					arg.workingLocation == null ? null : arg.workingLocation.name(), workEntry.getId());

			if (!arg.start.toLocalDate().equals(oldStart.toLocalDate())) {
				Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
				Resource oldWorkDay = tx.getResourceBy(TYPE_WORK_DAY, workEntry.getRelationId(PARAM_WORK_DAY), true);
				Resource newWorkDay = WorkDayHelper.getOrCreateWorkDay(tx, employee, arg.start);

				oldWorkDay.getStringListP(BAG_RELATIONS, PARAM_WORK_ENTRIES).removeValue(workEntry.getId());
				newWorkDay.addRelation(PARAM_WORK_ENTRIES, workEntry);
				workEntry.setRelation(PARAM_WORK_DAY, newWorkDay);

				tx.update(oldWorkDay);
				tx.update(newWorkDay);
			}

			workEntry.setDate(PARAM_START, arg.start);
			workEntry.setDate(PARAM_END, arg.end);
			workEntry.setString(PARAM_COMMENT, arg.comment != null ? arg.comment.trim() : "");
			workEntry.setString(PARAM_SOURCE, SOURCE_MANUAL);
			workEntry.setString(PARAM_WORKING_LOCATION, arg.workingLocation == null ? "" : arg.workingLocation.name());

			Resource scheduleVersion = ScheduleHelper.findScheduleVersion(tx, employeeId, arg.start.toLocalDate())
					.orElseThrow(() -> new IllegalStateException("No schedule version found for employee " + employeeId
							+ " on " + arg.start.toLocalDate()));
			workEntry.setRelation(PARAM_SCHEDULE, scheduleVersion);

			bumpVersion(workEntry, tx);
			tx.update(workEntry);

			String auditComment = arg.comment != null && !arg.comment.isBlank() ? arg.comment : "Work entry corrected/shortened";
			ChronivaroAuditHelper.audit(tx, TYPE_WORK_ENTRY, workEntry.getId(), AUDIT_ACTION_CORRECT, auditComment,
					"Corrected work entry " + workEntry.getId() + " for employee " + employeeId + " (start: " + oldStart
							+ " -> " + arg.start + ", end: " + oldEnd + " -> " + arg.end + ")");

			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	@Override
	public CorrectWorkEntryArgument getArgumentInstance() {
		return new CorrectWorkEntryArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	public static class CorrectWorkEntryArgument extends ServiceArgument {
		public String workEntryId;
		public ZonedDateTime start;
		public ZonedDateTime end;
		public String comment;
		public WorkingLocation workingLocation;
	}
}
