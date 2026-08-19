package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.AbsenceHelper;
import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.ScheduleHelper;
import ch.atexxi.chronivaro.core.model.VacationHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.LocalDate;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

public class ApproveAbsenceService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("absenceId must be set", arg.value);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, arg.value, true).getClone();

			if (!absence.getString(PARAM_STATE).equals(STATE_SUBMITTED)) {
				throw new IllegalStateException("Absence is not in state SUBMITTED!");
			}

			absence.setString(PARAM_STATE, STATE_APPROVED);
			bumpVersion(absence, tx);
			tx.update(absence);
			ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE, absence.getId(), AUDIT_ACTION_APPROVE,
					"Approved absence " + absence.getId() + " for employee " + absence.getRelationId(PARAM_EMPLOYEE));

			// If it's a vacation absence, check balance and create a vacation account entry
			if (VacationHelper.isVacationAbsence(tx, absence)) {
				String employeeId = absence.getRelationId(PARAM_EMPLOYEE);
				LocalDate start = absence.getDate(PARAM_START).toLocalDate();
				LocalDate end = absence.getDate(PARAM_END).toLocalDate();

				int totalMinutes = 0;
				for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
					totalMinutes += calculateMinutesForDay(tx, employeeId, absence, date);
				}

				if (totalMinutes > 0) {
					VacationHelper.assertSufficientVacationBalance(tx, employeeId, totalMinutes, absence.getDate(PARAM_START));

					Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
					entry.setName("Vacation Usage " + absence.getId());

					entry.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true));
					entry.setRelation(PARAM_ABSENCE, absence);
					entry.setDate(PARAM_DATE, absence.getDate(PARAM_START));
					entry.setString(PARAM_VACATION_TYPE, VACATION_USAGE);
					entry.setInteger(PARAM_VALUE, -totalMinutes);

					initVersion(entry, tx);
					tx.add(entry);
					ChronivaroAuditHelper.audit(tx, TYPE_VACATION_ACCOUNT_ENTRY, entry.getId(), AUDIT_ACTION_CREATE,
							"Created vacation usage entry for absence " + absence.getId() + " (" + totalMinutes + " minutes)");
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
