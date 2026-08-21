package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.AbsenceHelper;
import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.PeriodHelper;
import ch.atexxi.chronivaro.core.model.ScheduleHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.Operation;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.LocalDate;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

public class CancelAbsenceService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("absenceId must be set", arg.value);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, arg.value, true);
			tx.readLock(absence);

			String currentState = absence.getString(PARAM_STATE);
			if (!currentState.equals(STATE_DRAFT) && !currentState.equals(STATE_SUBMITTED) && !currentState.equals(STATE_APPROVED)) {
				throw new IllegalStateException("Absence in state " + currentState + " cannot be cancelled!");
			}

			Resource employee = tx.getResourceByRelation(absence, PARAM_EMPLOYEE, true);

			PeriodHelper.assertPeriodOpen(tx, employee.getId(), absence.getDate(PARAM_START).toLocalDate());
			if (!absence.getDate(PARAM_START).toLocalDate().equals(absence.getDate(PARAM_END).toLocalDate())) {
				PeriodHelper.assertPeriodOpen(tx, employee.getId(), absence.getDate(PARAM_END).toLocalDate());
			}

			// Authorization: Employee can only cancel own absence.
			// Supervisors/HR can cancel based on their UpdateResource privilege on Employee
			if (!tx.getPrivilegeContext().hasRole(ROLE_HR)
					&& !tx.getPrivilegeContext().hasRole(ROLE_ADMIN)) {

				Optional<Resource> currentEmployee = ChronivaroModelHelper.findEmployeeByUser(tx, tx.getCertificate().getUserId());
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
					"Cancelled absence " + absence.getId() + " for employee " + employee.getId());

			// If it was APPROVED and reduced vacation, we need to add back the vacation minutes
			if (oldState.equals(STATE_APPROVED)) {
				Resource absenceType = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
				if (absenceType.getBoolean(PARAM_REDUCE_VACATION_CREDIT)) {
					String employeeId = employee.getId();
					LocalDate start = absence.getDate(PARAM_START).toLocalDate();
					LocalDate end = absence.getDate(PARAM_END).toLocalDate();

					int totalMinutes = 0;
					for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
						totalMinutes += calculateMinutesForDay(tx, employeeId, absence, date);
					}

					if (totalMinutes > 0) {
						Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
						entry.setName("Vacation Cancellation " + absence.getId());

						entry.setRelation(PARAM_EMPLOYEE, employee);
						entry.setRelation(PARAM_ABSENCE, absence);
						entry.setDate(PARAM_DATE, absence.getDate(PARAM_START));
						entry.setString(PARAM_VACATION_TYPE, VACATION_CORRECTION);
						entry.setInteger(PARAM_VALUE, totalMinutes);
						entry.setString(PARAM_COMMENT, "Vacation cancellation refund for absence " + absence.getId());
						entry.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());

						initVersion(entry, tx);
						tx.add(entry);
						ChronivaroAuditHelper.audit(tx, TYPE_VACATION_ACCOUNT_ENTRY, entry.getId(), AUDIT_ACTION_CREATE,
								"Created vacation cancellation refund entry for absence " + absence.getId() + " (" + totalMinutes + " minutes)");
					}
				}
			}

			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	private int calculateMinutesForDay(StrolchTransaction tx, String employeeId, Resource absence, LocalDate date) {
		String durationType = absence.getString(PARAM_DURATION_TYPE);
		int targetMinutes = ScheduleHelper.getTargetMinutes(tx, employeeId, date);

		if (targetMinutes == 0)
			return 0;

		return switch (durationType) {
			case DURATION_FULL_DAY -> targetMinutes;
			case DURATION_HALF_DAY -> (int) Math.round(targetMinutes / 2.0);
			case DURATION_HOURS -> absence.getInteger(PARAM_MINUTES);
			default -> 0;
		};
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
