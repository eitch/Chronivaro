package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.AbsenceHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.VacationHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.initVersion;
import static java.text.MessageFormat.format;

public class ApproveAbsenceService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("absenceId must be set", arg.value);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, arg.value, true).getClone();

			if (!absence.getString(PARAM_STATE).equals(STATE_SUBMITTED)) {
				throw new IllegalStateException("Absence is not in state SUBMITTED!");
			}

			String employeeId = absence.getRelationId(PARAM_EMPLOYEE);
			Resource employee = ChronivaroModelHelper.getEmployee(tx, employeeId);
			ChronivaroModelHelper.assertCanManageEmployee(tx, employeeId);

			absence.setString(PARAM_STATE, STATE_APPROVED);
			bumpVersion(absence, tx);
			tx.update(absence);
			ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE, absence.getId(), AUDIT_ACTION_APPROVE,
					"Approved " + absence.getName() + " for employee " + employee.getString(PARAM_PERSONAL_NUMBER));

			// If it's a vacation absence, check balance and create a vacation account entry
			if (VacationHelper.isVacationAbsence(tx, absence)) {
				int totalMinutes = AbsenceHelper.getVacationMinutes(tx, employeeId, absence);

				if (totalMinutes > 0) {
					VacationHelper.assertSufficientVacationBalance(tx, employeeId, totalMinutes,
							absence.getDate(PARAM_START));

					Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
					entry.setName("Vacation Usage " + absence.getId());

					entry.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true));
					entry.setRelation(PARAM_ABSENCE, absence);
					entry.setDate(PARAM_DATE, absence.getDate(PARAM_START));
					entry.setDate(PARAM_CREATED_AT, java.time.ZonedDateTime.now());
					entry.setString(PARAM_VACATION_TYPE, VACATION_USAGE);
					entry.setInteger(PARAM_VALUE, -totalMinutes);
					entry.setString(PARAM_COMMENT, "Vacation usage for absence " + absence.getId());
					entry.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());

					initVersion(entry, tx);
					tx.add(entry);
					ChronivaroAuditHelper.audit(tx, TYPE_VACATION_ACCOUNT_ENTRY, entry.getId(), AUDIT_ACTION_CREATE,
							format("Created vacation usage entry for {0} ({1} minutes)", absence.getName(),
									totalMinutes));
				}
			}

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
