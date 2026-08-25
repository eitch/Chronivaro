package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.AbsenceHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.PeriodHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.Operation;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;
import java.util.Optional;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;

public class UpdateAbsenceService extends AbstractService<UpdateAbsenceService.UpdateAbsenceArgument, ServiceResult> {

	public static class UpdateAbsenceArgument extends ServiceArgument {
		public String absenceId;
		public String absenceTypeCode;
		public ZonedDateTime start;
		public ZonedDateTime end;
		public String durationType;
		public String dayPart;
		public Integer minutes;
		public String comment;
	}

	@Override
	protected ServiceResult internalDoService(UpdateAbsenceArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("absenceId must be set", arg.absenceId);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, arg.absenceId, true);
			tx.readLock(absence);

			String currentState = absence.getString(PARAM_STATE);
			if (!currentState.equals(STATE_DRAFT) && !currentState.equals(STATE_SUBMITTED)) {
				throw new IllegalStateException("Only absences in state DRAFT or SUBMITTED can be updated!");
			}

			Resource employee = tx.getResourceByRelation(absence, PARAM_EMPLOYEE, true);

			PeriodHelper.assertPeriodOpen(tx, employee.getId(), absence.getDate(PARAM_START).toLocalDate());
			if (arg.start != null) {
				PeriodHelper.assertPeriodOpen(tx, employee.getId(), arg.start.toLocalDate());
			}
			
			// Authorization: Only own absence or Supervisor/HR/Admin
			if (!tx.getPrivilegeContext().hasRole(ROLE_HR) && !tx.getPrivilegeContext().hasRole(ROLE_ADMIN)) {
				Optional<Resource> currentEmployee = ChronivaroModelHelper.findEmployeeByUser(tx, tx.getCertificate().getUserId());
				if (currentEmployee.isPresent() && currentEmployee.get().getId().equals(employee.getId())) {
					// Self-service: allowed
				} else {
					tx.assertHasPrivilege(Operation.UPDATE, employee);
				}
			}

			absence = absence.getClone();
			
			if (arg.absenceTypeCode != null) {
				Resource absenceType = AbsenceHelper.getAbsenceType(tx, arg.absenceTypeCode);
				absence.setRelation(PARAM_ABSENCE_TYPE, absenceType);
			}
			
			if (arg.start != null) absence.setDate(PARAM_START, arg.start);
			if (arg.end != null) absence.setDate(PARAM_END, arg.end);
			if (arg.durationType != null) absence.setString(PARAM_DURATION_TYPE, arg.durationType);
			if (arg.dayPart != null) absence.setString(PARAM_DAY_PART, arg.dayPart);
			if (arg.minutes != null) absence.setInteger(PARAM_MINUTES, arg.minutes);
			if (arg.comment != null) absence.setString(PARAM_COMMENT, arg.comment);

			Resource finalAbsenceType = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
			AbsenceHelper.validateDurationType(finalAbsenceType, absence.getString(PARAM_DURATION_TYPE));

			if (currentState.equals(STATE_SUBMITTED)) {
				String finalComment = absence.hasParameter(PARAM_COMMENT) ? absence.getString(PARAM_COMMENT) : "";
				AbsenceHelper.validateCommentRequired(finalAbsenceType, finalComment);
				AbsenceHelper.validateNoOverlap(tx, employee.getId(), absence.getDate(PARAM_START), absence.getDate(PARAM_END),
						absence.getId());
			}

			bumpVersion(absence, tx);
			tx.update(absence);

			ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE, absence.getId(), AUDIT_ACTION_UPDATE, arg.comment,
					"Updated absence " + absence.getId() + " for employee " + employee.getId());

			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	@Override
	public UpdateAbsenceArgument getArgumentInstance() {
		return new UpdateAbsenceArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
