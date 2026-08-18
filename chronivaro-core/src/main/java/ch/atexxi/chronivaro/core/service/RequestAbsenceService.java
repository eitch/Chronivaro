package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.AbsenceHelper;
import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

public class RequestAbsenceService
		extends AbstractService<RequestAbsenceService.RequestAbsenceArgument, ServiceResult> {

	public static class RequestAbsenceArgument extends ServiceArgument {
		public String employeeId;
		public String absenceTypeCode;
		public ZonedDateTime start;
		public ZonedDateTime end;
		public String durationType;
		public String dayPart;
		public int minutes;
		public String comment;
	}

	@Override
	protected ServiceResult internalDoService(RequestAbsenceArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.employeeId);
		DBC.PRE.assertNotEmpty("absenceTypeCode must be set", arg.absenceTypeCode);
		DBC.PRE.assertNotNull("start must be set", arg.start);
		DBC.PRE.assertNotNull("end must be set", arg.end);
		DBC.PRE.assertNotEmpty("durationType must be set", arg.durationType);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource absenceType = AbsenceHelper.getAbsenceType(tx, arg.absenceTypeCode);

			Resource absence = tx.getResourceTemplate(TYPE_ABSENCE, true);
			absence.setName("Absence " + arg.start);

			absence.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, arg.employeeId, true));
			absence.setRelation(PARAM_ABSENCE_TYPE, absenceType);
			absence.setDate(PARAM_START, arg.start);
			absence.setDate(PARAM_END, arg.end);
			absence.setString(PARAM_DURATION_TYPE, arg.durationType);
			if (arg.dayPart != null)
				absence.setString(PARAM_DAY_PART, arg.dayPart);
			if (arg.minutes > 0)
				absence.setInteger(PARAM_MINUTES, arg.minutes);
			if (arg.comment != null)
				absence.setString(PARAM_COMMENT, arg.comment);
			absence.setString(PARAM_STATE, STATE_SUBMITTED);

			initVersion(absence, tx);
			tx.add(absence);

			ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE, absence.getId(), AUDIT_ACTION_SUBMIT, arg.comment,
					"Requested absence for employee " + arg.employeeId + " (" + arg.absenceTypeCode + " from "
							+ arg.start + " to " + arg.end + ")");

			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	@Override
	public RequestAbsenceArgument getArgumentInstance() {
		return new RequestAbsenceArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
