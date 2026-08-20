package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.AbsenceHelper;
import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.PeriodHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringResult;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResultState;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

public class RequestAbsenceService
		extends AbstractService<RequestAbsenceService.RequestAbsenceArgument, StringResult> {

	public static class RequestAbsenceArgument extends ServiceArgument {
		public String employeeId;
		public String absenceTypeCode;
		public ZonedDateTime start;
		public ZonedDateTime end;
		public String durationType;
		public String dayPart;
		public int minutes;
		public String comment;
		public boolean asDraft;
		public String state;
	}

	@Override
	protected StringResult internalDoService(RequestAbsenceArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.employeeId);
		DBC.PRE.assertNotEmpty("absenceTypeCode must be set", arg.absenceTypeCode);
		DBC.PRE.assertNotNull("start must be set", arg.start);
		DBC.PRE.assertNotNull("end must be set", arg.end);
		DBC.PRE.assertNotEmpty("durationType must be set", arg.durationType);

		String absenceId;
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			PeriodHelper.assertPeriodOpen(tx, arg.employeeId, arg.start.toLocalDate());
			if (!arg.start.toLocalDate().equals(arg.end.toLocalDate())) {
				PeriodHelper.assertPeriodOpen(tx, arg.employeeId, arg.end.toLocalDate());
			}

			Resource absenceType = AbsenceHelper.getAbsenceType(tx, arg.absenceTypeCode);
			AbsenceHelper.validateDurationType(absenceType, arg.durationType);

			boolean isDraft = arg.asDraft || STATE_DRAFT.equalsIgnoreCase(arg.state);
			if (!isDraft) {
				AbsenceHelper.validateCommentRequired(absenceType, arg.comment);
				AbsenceHelper.validateNoOverlap(tx, arg.employeeId, arg.start, arg.end, null);
			}

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
			absence.setString(PARAM_STATE, isDraft ? STATE_DRAFT : STATE_SUBMITTED);

			initVersion(absence, tx);
			tx.add(absence);
			absenceId = absence.getId();

			if (isDraft) {
				ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE, absence.getId(), AUDIT_ACTION_CREATE, arg.comment,
						"Created draft absence for employee " + arg.employeeId + " (" + arg.absenceTypeCode + " from "
								+ arg.start + " to " + arg.end + ")");
			} else {
				ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE, absence.getId(), AUDIT_ACTION_SUBMIT, arg.comment,
						"Requested absence for employee " + arg.employeeId + " (" + arg.absenceTypeCode + " from "
								+ arg.start + " to " + arg.end + ")");
			}

			tx.commitOnClose();
		}

		return new StringResult(absenceId);
	}

	@Override
	public RequestAbsenceArgument getArgumentInstance() {
		return new RequestAbsenceArgument();
	}

	@Override
	public StringResult getResultInstance() {
		return new StringResult(ServiceResultState.FAILED);
	}
}
