package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.AbsenceHelper;
import ch.atexxi.chronivaro.core.model.ScheduleHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.LocalDate;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

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
			tx.update(absence);

			// If it's a vacation absence, create a vacation account entry
			Resource absenceType = tx.getResourceBy(TYPE_ABSENCE_TYPE, absence.getRelationId(PARAM_ABSENCE_TYPE), true);
			if (absenceType.getBoolean(PARAM_REDUCE_VACATION_CREDIT)) {
				String employeeId = absence.getRelationId(PARAM_EMPLOYEE);
				LocalDate start = absence.getDate(PARAM_START).toLocalDate();
				LocalDate end = absence.getDate(PARAM_END).toLocalDate();

				int totalMinutes = 0;
				for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
					totalMinutes += AbsenceHelper.getAbsenceMinutes(tx, employeeId, date);
				}

				// Wait, the above will return the current absence minutes ONLY IF it's already approved.
				// But we are in the process of approving it.
				// Let's recalculate manually for this absence.
				totalMinutes = 0;
				for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
					totalMinutes += calculateMinutesForDay(tx, employeeId, absence, date);
				}

				if (totalMinutes > 0) {
					Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
					entry.setName("Vacation Usage " + absence.getId());

					entry.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true));
					entry.setRelation(PARAM_ABSENCE, absence);
					entry.setDate(PARAM_DATE, absence.getDate(PARAM_START));
					entry.setString(PARAM_VACATION_TYPE, VACATION_USAGE);
					entry.setInteger(PARAM_VALUE, -totalMinutes);

					tx.add(entry);
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
