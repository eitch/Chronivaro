package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.ScheduleHelper;
import ch.eitchnet.chronivaro.core.model.WorkDayHelper;
import ch.eitchnet.chronivaro.core.model.WorkEntryHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Optional;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;

public class StopTimerService extends AbstractService<StopTimerService.StopTimerArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StopTimerArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.employeeId);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource employee = ChronivaroModelHelper.getEmployee(tx, arg.employeeId);
			Optional<Resource> activeEntryOpt = WorkEntryHelper.findActiveWorkEntry(tx, arg.employeeId);
			if (activeEntryOpt.isEmpty()) {
				throw new IllegalStateException("No active work entry found for this employee!");
			}

			Resource workEntry = activeEntryOpt.get();
			ZonedDateTime start = workEntry.getDate(PARAM_START);
			ZonedDateTime systemNow = ZonedDateTime.now(ChronivaroModelHelper.getEmployeeTimezone(employee));

			if (arg.time == null && !start.toLocalDate().equals(systemNow.toLocalDate())) {
				throw new IllegalStateException("Stop time must be explicitly supplied when stopping a timer from a previous day!");
			}

			ZonedDateTime now = arg.time != null ? arg.time : systemNow;

			if (now.isBefore(start)) {
				throw new IllegalStateException("Stop time cannot be before start time!");
			}

			String comment = arg.comment != null && !arg.comment.isBlank() ? arg.comment.trim() : null;

			if (start.toLocalDate().equals(now.toLocalDate())) {
				// Same day, just update
				Resource workEntryClone = workEntry.getClone();
				workEntryClone.setDate(PARAM_END, now);
				if (comment != null) {
					workEntryClone.setString(PARAM_COMMENT, comment);
				}
				WorkEntryHelper.validateNoOverlap(tx, arg.employeeId, start, now, workEntryClone.getId());
				tx.update(workEntryClone);
				ChronivaroAuditHelper.audit(tx, TYPE_WORK_ENTRY, workEntryClone.getId(), AUDIT_ACTION_STOP,
						"Stopped timer for employee " + arg.employeeId + " at " + now + (comment != null ? " (comment: " + comment + ")" : ""));
			} else if (now.toLocalDate().equals(start.toLocalDate().plusDays(1))) {
				// Next day carry-over
				ZonedDateTime midnight = start.toLocalDate().plusDays(1).atStartOfDay(start.getZone());

				// 1. Close current entry at midnight
				Resource workEntryClone = workEntry.getClone();
				workEntryClone.setDate(PARAM_END, midnight);
				if (comment != null) {
					workEntryClone.setString(PARAM_COMMENT, comment);
				}
				tx.update(workEntryClone);
				ChronivaroAuditHelper.audit(tx, TYPE_WORK_ENTRY, workEntryClone.getId(), AUDIT_ACTION_STOP,
						"Split timer at midnight for employee " + arg.employeeId + " (start=" + start + ", end=" + midnight + ")");

				// 2. Create new WorkEntry on the next day
				Resource workDay = WorkDayHelper.getOrCreateWorkDay(tx, employee, now);
				Resource nextWorkEntry = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
				nextWorkEntry.setName("WorkEntry " + midnight);
				nextWorkEntry.setRelation(PARAM_EMPLOYEE, employee);
				nextWorkEntry.setRelation(PARAM_WORK_DAY, workDay);
				nextWorkEntry.setDate(PARAM_START, midnight);
				nextWorkEntry.setDate(PARAM_END, now);
				nextWorkEntry.setString(PARAM_SOURCE, SOURCE_TIMER);
				nextWorkEntry.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());
				if (comment != null) {
					nextWorkEntry.setString(PARAM_COMMENT, comment);
				}
				
				Resource scheduleVersion = ScheduleHelper.findScheduleVersion(tx, arg.employeeId).orElseThrow();
				nextWorkEntry.setRelation(PARAM_SCHEDULE, scheduleVersion);

				tx.add(nextWorkEntry);
				workDay.addRelation(PARAM_WORK_ENTRIES, nextWorkEntry);
				tx.update(workDay);
				ChronivaroAuditHelper.audit(tx, TYPE_WORK_ENTRY, nextWorkEntry.getId(), AUDIT_ACTION_CREATE,
						"Created split timer entry for employee " + arg.employeeId + " (start=" + midnight + ", end=" + now + ")");
			} else {
				// Forgotten timer (more than one day)
				// Cap at daily target, but no later than midnight
				int targetMinutes = ScheduleHelper.getTargetMinutes(tx, arg.employeeId, start.toLocalDate());
				int currentMinutes = tx.getResourcesByRelation(WorkDayHelper.getOrCreateWorkDay(tx, employee, start), PARAM_WORK_ENTRIES, true)
						.stream()
						.filter(we -> we.hasParameter(PARAM_END) && we.getDate(PARAM_END).getYear() != 1970)
						.mapToInt(we -> (int) (we.getDate(PARAM_END).toEpochSecond() - we.getDate(PARAM_START).toEpochSecond()) / 60)
						.sum();

				int remainingMinutes = Math.max(0, targetMinutes - currentMinutes);
				ZonedDateTime end = start.plusMinutes(remainingMinutes);

				ZonedDateTime midnight = start.toLocalDate().plusDays(1).atStartOfDay(start.getZone());
				if (end.isAfter(midnight))
					end = midnight;

				Resource workEntryClone = workEntry.getClone();
				workEntryClone.setDate(PARAM_END, end);
				String autoComment = "Timer vergessen - auf Sollzeit begrenzt";
				workEntryClone.setString(PARAM_COMMENT, comment != null ? autoComment + ": " + comment : autoComment);
				tx.update(workEntryClone);
				ChronivaroAuditHelper.audit(tx, TYPE_WORK_ENTRY, workEntryClone.getId(), AUDIT_ACTION_STOP,
						"Capped forgotten timer for employee " + arg.employeeId + " at " + end);
			}

			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	@Override
	public StopTimerArgument getArgumentInstance() {
		return new StopTimerArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	public static class StopTimerArgument extends ServiceArgument {
		public String employeeId;
		public ZonedDateTime time;
		public String comment;

		public StopTimerArgument() {
		}

		public StopTimerArgument(String employeeId) {
			this.employeeId = employeeId;
		}

		public StopTimerArgument(String employeeId, String comment) {
			this.employeeId = employeeId;
			this.comment = comment;
		}

		public StopTimerArgument(String employeeId, ZonedDateTime time, String comment) {
			this.employeeId = employeeId;
			this.time = time;
			this.comment = comment;
		}
	}
}
