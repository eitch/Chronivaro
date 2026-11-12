package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.notification.ChronivaroNotificationHelper;
import ch.eitchnet.chronivaro.core.notification.Notification;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.util.List;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;
import static java.text.MessageFormat.format;

public class RejectAbsenceService extends AbstractChronivaroService<RejectAbsenceService.RejectAbsenceArgument,
		ServiceResult> {

	@Override
	protected ServiceResult internalDoService(RejectAbsenceArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("absenceId must be set", arg.absenceId);
		DBC.PRE.assertNotEmpty("comment must be set", arg.comment);

		List<Notification> notifications;
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, arg.absenceId, true);
			tx.readLock(absence);

			if (!absence.getString(PARAM_STATE).equals(STATE_SUBMITTED)) {
				throw new IllegalStateException("Absence is not in state SUBMITTED!");
			}

			// Authorisation check: supervisor may only act on employees within their permitted scope
			String employeeId = absence.getRelationId(PARAM_EMPLOYEE);
			Resource employee = ChronivaroModelHelper.getEmployee(tx, employeeId);
			ChronivaroModelHelper.assertCanManageEmployee(tx, employeeId);

			absence = absence.getClone();
			absence.setString(PARAM_STATE, STATE_REJECTED);
			absence.setString(PARAM_COMMENT, arg.comment);
			bumpVersion(absence, tx);
			tx.update(absence);

			ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE, absence.getId(), AUDIT_ACTION_REJECT, arg.comment,
					format("Rejected absence {0} for employee {1}", absence.getName(),
						employee.getString(PARAM_PERSONAL_NUMBER)));
			notifications = ChronivaroNotificationHelper.absenceDecision(tx, getAgent(), employeeId,
					absence.getDate(PARAM_START), absence.getDate(PARAM_END), "rejected", tx.getCertificate().getUserId(),
					tx.getCertificate().getUsername());

			tx.commitOnClose();
		}

		dispatchNotifications(notifications);
		return ServiceResult.success();
	}

	public static class RejectAbsenceArgument extends ServiceArgument {
		public String absenceId;
		public String comment;

		public RejectAbsenceArgument() {
		}

		public RejectAbsenceArgument(String absenceId, String comment) {
			this.absenceId = absenceId;
			this.comment = comment;
		}
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
