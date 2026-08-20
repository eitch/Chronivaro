package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.AbsenceHelper;
import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.PeriodHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.Operation;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;

public class SubmitAbsenceService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("absenceId must be set", arg.value);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, arg.value, true);
			tx.readLock(absence);

			String currentState = absence.getString(PARAM_STATE);
			if (!currentState.equals(STATE_DRAFT)) {
				throw new IllegalStateException("Only draft absences can be submitted! Current state is " + currentState);
			}

			Resource employee = tx.getResourceByRelation(absence, PARAM_EMPLOYEE, true);

			PeriodHelper.assertPeriodOpen(tx, employee.getId(), absence.getDate(PARAM_START).toLocalDate());
			if (!absence.getDate(PARAM_START).toLocalDate().equals(absence.getDate(PARAM_END).toLocalDate())) {
				PeriodHelper.assertPeriodOpen(tx, employee.getId(), absence.getDate(PARAM_END).toLocalDate());
			}

			// Authorization: Only own absence or Supervisor/HR/Admin
			if (!tx.getPrivilegeContext().hasRole(ROLE_HR) && !tx.getPrivilegeContext().hasRole(ROLE_ADMIN)) {
				Optional<Resource> currentEmployee = ChronivaroModelHelper.findEmployeeByUser(tx,
						tx.getCertificate().getUserId());
				if (currentEmployee.isPresent() && currentEmployee.get().getId().equals(employee.getId())) {
					// Self-service: allowed
				} else {
					tx.assertHasPrivilege(Operation.UPDATE, employee);
				}
			}

			Resource absenceType = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
			AbsenceHelper.validateDurationType(absenceType, absence.getString(PARAM_DURATION_TYPE));

			String comment = absence.hasParameter(PARAM_COMMENT) ? absence.getString(PARAM_COMMENT) : "";
			AbsenceHelper.validateCommentRequired(absenceType, comment);
			AbsenceHelper.validateNoOverlap(tx, employee.getId(), absence.getDate(PARAM_START),
					absence.getDate(PARAM_END), absence.getId());

			absence = absence.getClone();
			absence.setString(PARAM_STATE, STATE_SUBMITTED);

			bumpVersion(absence, tx);
			tx.update(absence);

			ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE, absence.getId(), AUDIT_ACTION_SUBMIT, comment,
					"Submitted draft absence " + absence.getId() + " for employee " + employee.getId());

			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	@Override
	public StringArgument getArgumentInstance() {
		return new StringArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
