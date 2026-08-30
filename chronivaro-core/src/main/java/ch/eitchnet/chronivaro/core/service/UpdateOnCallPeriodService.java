package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;

public class UpdateOnCallPeriodService extends AbstractService<UpdateOnCallPeriodService.UpdateOnCallPeriodArgument, ServiceResult> {

	public static class UpdateOnCallPeriodArgument extends ServiceArgument {
		public String id;
		public LocalDate startDate;
		public String startTime;
		public LocalDate endDate;
		public String endTime;
		public String comment;
	}

	@Override
	protected ServiceResult internalDoService(UpdateOnCallPeriodArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("id is required", arg.id);
		DBC.PRE.assertNotNull("startDate is required", arg.startDate);
		DBC.PRE.assertNotNull("endDate is required", arg.endDate);

		if (arg.endDate.isBefore(arg.startDate)) {
			throw new IllegalArgumentException("endDate cannot be before startDate");
		}

		if (arg.startTime != null && !arg.startTime.isBlank()) {
			LocalTime.parse(arg.startTime.trim());
		}
		if (arg.endTime != null && !arg.endTime.isBlank()) {
			LocalTime.parse(arg.endTime.trim());
		}

		if (arg.startDate.equals(arg.endDate) && arg.startTime != null && !arg.startTime.isBlank() && arg.endTime != null && !arg.endTime.isBlank()) {
			LocalTime st = LocalTime.parse(arg.startTime.trim());
			LocalTime et = LocalTime.parse(arg.endTime.trim());
			if (et.isBefore(st)) {
				throw new IllegalArgumentException("endTime cannot be before startTime on the same day");
			}
		}

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource onCallPeriod = tx.getResourceBy(TYPE_ON_CALL_PERIOD, arg.id, true);
			tx.readLock(onCallPeriod);

			String employeeId = onCallPeriod.getRelationId(PARAM_EMPLOYEE);
			ChronivaroModelHelper.assertCanManageEmployee(tx, employeeId);

			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
			ZoneId tz = ChronivaroModelHelper.getEmployeeTimezone(employee);

			ZonedDateTime startZdt = arg.startDate.atStartOfDay(tz);
			ZonedDateTime endZdt = arg.endDate.atTime(23, 59, 59, 999_000_000).atZone(tz);

			onCallPeriod.setDate(PARAM_START_DATE, startZdt);
			onCallPeriod.setString(PARAM_START_TIME, arg.startTime != null ? arg.startTime.trim() : "");
			onCallPeriod.setDate(PARAM_END_DATE, endZdt);
			onCallPeriod.setString(PARAM_END_TIME, arg.endTime != null ? arg.endTime.trim() : "");
			onCallPeriod.setString(PARAM_COMMENT, arg.comment != null ? arg.comment.trim() : "");

			bumpVersion(onCallPeriod, tx);

			tx.update(onCallPeriod);

			ChronivaroAuditHelper.audit(tx, TYPE_ON_CALL_PERIOD, arg.id, AUDIT_ACTION_UPDATE,
					"Updated on-call period for employee " + employeeId + " (" + arg.startDate + " - " + arg.endDate + ")");

			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	@Override
	public UpdateOnCallPeriodArgument getArgumentInstance() {
		return new UpdateOnCallPeriodArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
