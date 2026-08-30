package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;
import li.strolch.utils.helper.StringHelper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

public class CreateOnCallPeriodService extends AbstractService<CreateOnCallPeriodService.CreateOnCallPeriodArgument, ServiceResult> {

	public static class CreateOnCallPeriodArgument extends ServiceArgument {
		public String employeeId;
		public LocalDate startDate;
		public String startTime;
		public LocalDate endDate;
		public String endTime;
		public String comment;
	}

	@Override
	protected ServiceResult internalDoService(CreateOnCallPeriodArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("employeeId is required", arg.employeeId);
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
			ChronivaroModelHelper.assertCanManageEmployee(tx, arg.employeeId);

			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, arg.employeeId, true);
			ZoneId tz = ChronivaroModelHelper.getEmployeeTimezone(employee);

			String id = StringHelper.getUniqueId();
			Resource onCallPeriod = tx.getResourceTemplate(TYPE_ON_CALL_PERIOD).getClone();
			onCallPeriod.setId(id);
			onCallPeriod.setName("On-Call " + arg.employeeId + " (" + arg.startDate + " to " + arg.endDate + ")");

			ZonedDateTime startZdt = arg.startDate.atStartOfDay(tz);
			ZonedDateTime endZdt = arg.endDate.atTime(23, 59, 59, 999_000_000).atZone(tz);

			onCallPeriod.setDate(PARAM_START_DATE, startZdt);
			onCallPeriod.setString(PARAM_START_TIME, arg.startTime != null ? arg.startTime.trim() : "");
			onCallPeriod.setDate(PARAM_END_DATE, endZdt);
			onCallPeriod.setString(PARAM_END_TIME, arg.endTime != null ? arg.endTime.trim() : "");
			onCallPeriod.setString(PARAM_COMMENT, arg.comment != null ? arg.comment.trim() : "");
			onCallPeriod.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());
			onCallPeriod.setRelationId(PARAM_EMPLOYEE, arg.employeeId);

			initVersion(onCallPeriod, tx);

			tx.add(onCallPeriod);

			ChronivaroAuditHelper.audit(tx, TYPE_ON_CALL_PERIOD, id, AUDIT_ACTION_CREATE,
					"Created on-call period for employee " + arg.employeeId + " (" + arg.startDate + " - " + arg.endDate + ")");

			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	@Override
	public CreateOnCallPeriodArgument getArgumentInstance() {
		return new CreateOnCallPeriodArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
