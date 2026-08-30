package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.PeriodHelper;
import ch.eitchnet.chronivaro.core.model.ScheduleHelper;
import ch.eitchnet.chronivaro.core.model.WorkDayHelper;
import ch.eitchnet.chronivaro.core.model.WorkEntryHelper;
import ch.eitchnet.chronivaro.core.model.WorkingLocation;
import li.strolch.exception.StrolchModelException;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;
import java.util.Optional;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

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

			boolean spansMidnight = arg.end.toLocalDate().equals(arg.start.toLocalDate().plusDays(1));
			if (!arg.start.toLocalDate().equals(arg.end.toLocalDate()) && !spansMidnight) {
				throw new IllegalArgumentException("Work entry must start and end on the same day or end on the next day!");
			}

			if (spansMidnight) {
				PeriodHelper.assertPeriodOpen(tx, employeeId, arg.end.toLocalDate());
			}

			boolean isAdminOrHr = tx.getPrivilegeContext().hasRole(ROLE_HR)
					|| tx.getPrivilegeContext().hasRole(ROLE_ADMIN)
					|| tx.getPrivilegeContext().hasRole(ROLE_ADMINISTRATOR);

			if (!isAdminOrHr) {
				Optional<Resource> callerEmployee = ChronivaroModelHelper.findEmployeeByUser(tx, tx.getCertificate().getUserId());
				boolean isSelf = callerEmployee.isPresent() && callerEmployee.get().getId().equals(employeeId);

				if (isSelf) {
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

			if (spansMidnight) {
				ZonedDateTime midnight = arg.start.toLocalDate().plusDays(1).atStartOfDay(arg.start.getZone());

				WorkEntryHelper.validateNoOverlap(tx, employeeId, arg.start, midnight, workEntry.getId());
				WorkEntryHelper.validateWorkingLocation(tx, employeeId, arg.start, midnight,
						arg.workingLocation == null ? null : arg.workingLocation.name(), workEntry.getId());

				WorkEntryHelper.validateNoOverlap(tx, employeeId, midnight, arg.end, null);
				WorkEntryHelper.validateWorkingLocation(tx, employeeId, midnight, arg.end,
						arg.workingLocation == null ? null : arg.workingLocation.name(), null);

				// 1. Update first entry up to midnight
				workEntry.setDate(PARAM_START, arg.start);
				workEntry.setDate(PARAM_END, midnight);
				workEntry.setString(PARAM_COMMENT, arg.comment != null ? arg.comment.trim() : "");
				if (!workEntry.hasParameter(PARAM_SOURCE) || workEntry.getString(PARAM_SOURCE).isBlank()) {
					workEntry.setString(PARAM_SOURCE, SOURCE_MANUAL);
				}
				workEntry.setString(PARAM_WORKING_LOCATION, arg.workingLocation == null ? "" : arg.workingLocation.name());
				if (arg.isOnCall != null) {
					workEntry.setBoolean(PARAM_IS_ON_CALL, arg.isOnCall);
				}

				Resource scheduleVersion = ScheduleHelper.findScheduleVersion(tx, employeeId, arg.start.toLocalDate())
						.orElseThrow(() -> new IllegalStateException("No schedule version found for employee " + employeeId
								+ " on " + arg.start.toLocalDate()));
				workEntry.setRelation(PARAM_SCHEDULE, scheduleVersion);

				bumpVersion(workEntry, tx);
				tx.update(workEntry);

				String auditComment = arg.comment != null && !arg.comment.isBlank() ? arg.comment : "Work entry corrected";
				ChronivaroAuditHelper.audit(tx, TYPE_WORK_ENTRY, workEntry.getId(), AUDIT_ACTION_CORRECT, auditComment,
						"Corrected work entry " + workEntry.getId() + " for employee " + employeeId + " (split at midnight: start: " + oldStart
								+ " -> " + arg.start + ", end: " + oldEnd + " -> " + midnight + ")");

				// 2. Create second entry on next day
				Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
				Resource nextWorkDay = WorkDayHelper.getOrCreateWorkDay(tx, employee, arg.end);
				Resource nextWorkEntry = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
				nextWorkEntry.setName("WorkEntry " + midnight);
				nextWorkEntry.setRelation(PARAM_EMPLOYEE, employee);
				nextWorkEntry.setRelation(PARAM_WORK_DAY, nextWorkDay);
				nextWorkEntry.setDate(PARAM_START, midnight);
				nextWorkEntry.setDate(PARAM_END, arg.end);
				nextWorkEntry.setString(PARAM_SOURCE, SOURCE_MANUAL);
				nextWorkEntry.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());
				nextWorkEntry.setString(PARAM_COMMENT, arg.comment != null ? arg.comment.trim() : "");
				nextWorkEntry.setString(PARAM_WORKING_LOCATION, arg.workingLocation == null ? "" : arg.workingLocation.name());
				if (arg.isOnCall != null) {
					nextWorkEntry.setBoolean(PARAM_IS_ON_CALL, arg.isOnCall);
				} else if (workEntry.hasParameter(PARAM_IS_ON_CALL)) {
					nextWorkEntry.setBoolean(PARAM_IS_ON_CALL, workEntry.getBoolean(PARAM_IS_ON_CALL));
				}

				Resource nextScheduleVersion = ScheduleHelper.findScheduleVersion(tx, employeeId, arg.end.toLocalDate())
						.orElseThrow(() -> new IllegalStateException("No schedule version found for employee " + employeeId
								+ " on " + arg.end.toLocalDate()));
				nextWorkEntry.setRelation(PARAM_SCHEDULE, nextScheduleVersion);

				initVersion(nextWorkEntry, tx);
				tx.add(nextWorkEntry);
				nextWorkDay.addRelation(PARAM_WORK_ENTRIES, nextWorkEntry);
				tx.update(nextWorkDay);

				ChronivaroAuditHelper.audit(tx, TYPE_WORK_ENTRY, nextWorkEntry.getId(), AUDIT_ACTION_CREATE, auditComment,
						"Created split work entry for employee " + employeeId + " from " + midnight + " to " + arg.end
								+ " following work entry correction " + workEntry.getId());
			} else {
				WorkEntryHelper.validateNoOverlap(tx, employeeId, arg.start, arg.end, workEntry.getId());
				WorkEntryHelper.validateWorkingLocation(tx, employeeId, arg.start, arg.end,
						arg.workingLocation == null ? null : arg.workingLocation.name(), workEntry.getId());

				workEntry.setDate(PARAM_START, arg.start);
				workEntry.setDate(PARAM_END, arg.end);
				workEntry.setString(PARAM_COMMENT, arg.comment != null ? arg.comment.trim() : "");
				if (!workEntry.hasParameter(PARAM_SOURCE) || workEntry.getString(PARAM_SOURCE).isBlank()) {
					workEntry.setString(PARAM_SOURCE, SOURCE_MANUAL);
				}
				workEntry.setString(PARAM_WORKING_LOCATION, arg.workingLocation == null ? "" : arg.workingLocation.name());
				if (arg.isOnCall != null) {
					workEntry.setBoolean(PARAM_IS_ON_CALL, arg.isOnCall);
				}

				Resource scheduleVersion = ScheduleHelper.findScheduleVersion(tx, employeeId, arg.start.toLocalDate())
						.orElseThrow(() -> new IllegalStateException("No schedule version found for employee " + employeeId
								+ " on " + arg.start.toLocalDate()));
				workEntry.setRelation(PARAM_SCHEDULE, scheduleVersion);

				bumpVersion(workEntry, tx);
				tx.update(workEntry);

				String auditComment = arg.comment != null && !arg.comment.isBlank() ? arg.comment : "Work entry corrected";
				ChronivaroAuditHelper.audit(tx, TYPE_WORK_ENTRY, workEntry.getId(), AUDIT_ACTION_CORRECT, auditComment,
						"Corrected work entry " + workEntry.getId() + " for employee " + employeeId + " (start: " + oldStart
								+ " -> " + arg.start + ", end: " + oldEnd + " -> " + arg.end + ")");
			}

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
		public Boolean isOnCall;
	}
}
