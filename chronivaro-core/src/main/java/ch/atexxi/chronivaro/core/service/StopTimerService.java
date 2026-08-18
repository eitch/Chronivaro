package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.ScheduleHelper;
import ch.atexxi.chronivaro.core.model.WorkDayHelper;
import ch.atexxi.chronivaro.core.model.WorkEntryHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class StopTimerService extends AbstractService<StopTimerService.StopTimerArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StopTimerArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.employeeId);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource employee = ChronivaroModelHelper.getEmployee(tx, arg.employeeId);
			ZonedDateTime now = arg.time != null ? arg.time : ZonedDateTime.now(ChronivaroModelHelper.getEmployeeTimezone(employee));

			Optional<Resource> activeEntryOpt = WorkEntryHelper.findActiveWorkEntry(tx, arg.employeeId);
			if (activeEntryOpt.isEmpty()) {
				throw new IllegalStateException("No active work entry found for this employee!");
			}

			Resource workEntry = activeEntryOpt.get();
			ZonedDateTime start = workEntry.getDate(PARAM_START);

			if (now.isBefore(start)) {
				throw new IllegalStateException("Stop time cannot be before start time!");
			}

			if (start.toLocalDate().equals(now.toLocalDate())) {
				// Same day, just update
				Resource workEntryClone = workEntry.getClone();
				workEntryClone.setDate(PARAM_END, now);
				WorkEntryHelper.validateNoOverlap(tx, arg.employeeId, start, now, workEntryClone.getId());
				tx.update(workEntryClone);
				ChronivaroAuditHelper.audit(tx, TYPE_WORK_ENTRY, workEntryClone.getId(), AUDIT_ACTION_STOP,
						"Stopped timer for employee " + arg.employeeId + " at " + now);
			} else if (now.toLocalDate().equals(start.toLocalDate().plusDays(1))) {
				// Next day carry-over
				ZonedDateTime midnight = start.toLocalDate().plusDays(1).atStartOfDay(start.getZone());

				// 1. Close current entry at midnight
				Resource workEntryClone = workEntry.getClone();
				workEntryClone.setDate(PARAM_END, midnight);
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
				workEntryClone.setString(PARAM_COMMENT, "Timer vergessen - auf Sollzeit begrenzt");
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

		public StopTimerArgument() {
		}

		public StopTimerArgument(String employeeId) {
			this.employeeId = employeeId;
		}
	}
}
