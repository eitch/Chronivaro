package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.PeriodHelper;
import ch.eitchnet.chronivaro.core.model.VacationHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.Operation;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.util.Optional;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.initVersion;
import static java.text.MessageFormat.format;

public class CancelAbsenceService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("absenceId must be set", arg.value);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, arg.value, true);
			tx.readLock(absence);

			String currentState = absence.getString(PARAM_STATE);
			if (!currentState.equals(STATE_DRAFT) && !currentState.equals(STATE_SUBMITTED) && !currentState.equals(
					STATE_APPROVED)) {
				throw new IllegalStateException("Absence in state " + currentState + " cannot be cancelled!");
			}

			Resource employee = tx.getResourceByRelation(absence, PARAM_EMPLOYEE, true);

			PeriodHelper.assertPeriodOpen(tx, employee.getId(), absence.getDate(PARAM_START).toLocalDate());
			if (!absence.getDate(PARAM_START).toLocalDate().equals(absence.getDate(PARAM_END).toLocalDate())) {
				PeriodHelper.assertPeriodOpen(tx, employee.getId(), absence.getDate(PARAM_END).toLocalDate());
			}

			// Authorization: Employee can only cancel own absence.
			// Supervisors/HR can cancel based on their UpdateResource privilege on Employee
			if (!tx.getPrivilegeContext().hasRole(ROLE_HR) && !tx.getPrivilegeContext().hasRole(ROLE_ADMIN)) {

				Optional<Resource> currentEmployee = ChronivaroModelHelper.findEmployeeByUser(tx,
						tx.getCertificate().getUserId());
				if (currentEmployee.isPresent() && currentEmployee.get().getId().equals(employee.getId())) {
					// Self-service: allowed
				} else {
					// Not self-service, check for supervisor privilege
					tx.assertHasPrivilege(Operation.UPDATE, employee);
				}
			}

			// Specification: "Past absences cannot be cancelled if the specification forbids this."
			// Assumption: Past absences can be cancelled as long as they are not locked by a period.
			// Let's check if the period is locked.
			// (Assuming period locking is handled by tx.update(absence) if implemented, 
			// but better check explicitly if there's a mechanism).
			// For now, let's just proceed as the state check SUBMITTED/APPROVED is primary.

			String oldState = absence.getString(PARAM_STATE);
			absence = absence.getClone();
			absence.setString(PARAM_STATE, STATE_CANCELLED);
			bumpVersion(absence, tx);
			tx.update(absence);
			ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE, absence.getId(), AUDIT_ACTION_CANCEL,
					format("Cancelled absence {0} for employee {1}", absence.getName(),
							employee.getString(PARAM_PERSONAL_NUMBER)));

			// If it was APPROVED and reduced vacation, we need to add back the vacation minutes
			if (oldState.equals(STATE_APPROVED)) {
				if (VacationHelper.isVacationAbsence(tx, absence)) {
					String absenceId = absence.getId();
					int totalMinutes = tx
							.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
							.filter(entry -> entry.hasRelation(PARAM_ABSENCE) && absenceId.equals(
									entry.getRelationId(PARAM_ABSENCE)))
							.filter(entry -> VACATION_USAGE.equals(entry.getString(PARAM_VACATION_TYPE)))
							.mapToInt(entry -> entry.getInteger(PARAM_VALUE) < 0 ? -entry.getInteger(PARAM_VALUE) : 0)
							.sum();

					if (totalMinutes > 0) {
						Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
						entry.setName("Vacation Cancellation for absense " + absence.getName());

						entry.setRelation(PARAM_EMPLOYEE, employee);
						entry.setRelation(PARAM_ABSENCE, absence);
						entry.setDate(PARAM_DATE, absence.getDate(PARAM_START));
						entry.setDate(PARAM_CREATED_AT, java.time.ZonedDateTime.now());
						entry.setString(PARAM_VACATION_TYPE, VACATION_CORRECTION);
						entry.setInteger(PARAM_VALUE, totalMinutes);
						entry.setString(PARAM_COMMENT, "Vacation cancellation refund for absence " + absence.getName());
						entry.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());

						initVersion(entry, tx);
						tx.add(entry);
						ChronivaroAuditHelper.audit(tx, TYPE_VACATION_ACCOUNT_ENTRY, entry.getId(), AUDIT_ACTION_CREATE,
								format("Created vacation cancellation refund entry for absence {0} ({1} minutes)",
										absence.getName(), totalMinutes));
					}
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
