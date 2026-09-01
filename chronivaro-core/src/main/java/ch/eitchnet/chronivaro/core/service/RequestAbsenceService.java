package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.AbsenceHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.PeriodHelper;
import ch.eitchnet.chronivaro.core.model.ScheduleHelper;
import ch.eitchnet.chronivaro.core.model.VacationHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringResult;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResultState;
import li.strolch.utils.dbc.DBC;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

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
		public boolean directApprove;
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
			// Check if actor is acting on behalf of another employee
			Optional<Resource> currentEmp = ChronivaroModelHelper.findEmployeeByUser(tx, tx.getCertificate().getUserId());
			boolean isSelf = currentEmp.isPresent() && currentEmp.get().getId().equals(arg.employeeId);

			if (!isSelf) {
				ChronivaroModelHelper.assertCanManageEmployee(tx, arg.employeeId);
			}

			boolean isApproved = arg.directApprove || STATE_APPROVED.equalsIgnoreCase(arg.state);
			if (isApproved && isSelf) {
				// Employees cannot directly self-approve absences
				throw new IllegalArgumentException("Employees cannot self-approve absence requests.");
			}

			PeriodHelper.assertPeriodOpen(tx, arg.employeeId, arg.start.toLocalDate());
			if (!arg.start.toLocalDate().equals(arg.end.toLocalDate())) {
				PeriodHelper.assertPeriodOpen(tx, arg.employeeId, arg.end.toLocalDate());
			}

			Resource absenceType = AbsenceHelper.getAbsenceType(tx, arg.absenceTypeCode);
			AbsenceHelper.validateDurationType(absenceType, arg.durationType);

			boolean isDraft = !isApproved && (arg.asDraft || STATE_DRAFT.equalsIgnoreCase(arg.state));
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
			absence.setString(PARAM_STATE, isApproved ? STATE_APPROVED : (isDraft ? STATE_DRAFT : STATE_SUBMITTED));
			absence.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());

			initVersion(absence, tx);
			tx.add(absence);
			absenceId = absence.getId();

			if (isApproved) {
				// Handle vacation usage if applicable
				if (VacationHelper.isVacationAbsence(tx, absence)) {
					LocalDate start = absence.getDate(PARAM_START).toLocalDate();
					LocalDate end = absence.getDate(PARAM_END).toLocalDate();

					int totalMinutes = 0;
					for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
						totalMinutes += calculateMinutesForDay(tx, arg.employeeId, absence, date);
					}

					if (totalMinutes > 0) {
						VacationHelper.assertSufficientVacationBalance(tx, arg.employeeId, totalMinutes, absence.getDate(PARAM_START));

						Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
						entry.setName("Vacation Usage " + absence.getId());

						entry.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, arg.employeeId, true));
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
								"Created vacation usage entry for absence " + absence.getId() + " (" + totalMinutes + " minutes)");
					}
				}

				ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE, absence.getId(), AUDIT_ACTION_CREATE, arg.comment,
						"Created and approved absence for employee " + arg.employeeId + " (" + arg.absenceTypeCode + " from "
								+ arg.start + " to " + arg.end + ") by " + tx.getCertificate().getUsername());
			} else if (isDraft) {
				ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE, absence.getId(), AUDIT_ACTION_CREATE, arg.comment,
						"Created draft absence for employee " + arg.employeeId + " (" + arg.absenceTypeCode + " from "
								+ arg.start + " to " + arg.end + ") by " + tx.getCertificate().getUsername());
			} else {
				ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE, absence.getId(), AUDIT_ACTION_SUBMIT, arg.comment,
						"Requested absence for employee " + arg.employeeId + " (" + arg.absenceTypeCode + " from "
								+ arg.start + " to " + arg.end + ") by " + tx.getCertificate().getUsername());
			}

			tx.commitOnClose();
		}

		return new StringResult(absenceId);
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
	public RequestAbsenceArgument getArgumentInstance() {
		return new RequestAbsenceArgument();
	}

	@Override
	public StringResult getResultInstance() {
		return new StringResult(ServiceResultState.FAILED);
	}
}
