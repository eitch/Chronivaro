package ch.atexxi.chronivaro.core.service;

import li.strolch.model.Resource;
import li.strolch.persistence.api.Operation;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class RejectAbsenceService extends AbstractService<RejectAbsenceService.RejectAbsenceArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(RejectAbsenceArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("absenceId must be set", arg.absenceId);
		DBC.PRE.assertNotEmpty("comment must be set", arg.comment);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, arg.absenceId, true);
			tx.readLock(absence);

			if (!absence.getString(PARAM_STATE).equals(STATE_SUBMITTED)) {
				throw new IllegalStateException("Absence is not in state SUBMITTED!");
			}

			// Authorisation check: supervisor may only act on employees within their permitted scope
			Resource employee = tx.getResourceByRelation(absence, PARAM_EMPLOYEE, true);
			tx.assertHasPrivilege(Operation.UPDATE, employee);

			absence = absence.getClone();
			absence.setString(PARAM_STATE, STATE_REJECTED);
			absence.setString(PARAM_COMMENT, arg.comment);
			tx.update(absence);

			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	public static class RejectAbsenceArgument extends ServiceArgument {
		public String absenceId;
		public String comment;
	}

	@Override
	public RejectAbsenceArgument getArgumentInstance() {
		return new RejectAbsenceArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
