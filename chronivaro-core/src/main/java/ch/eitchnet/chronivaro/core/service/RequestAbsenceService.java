package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.*;
import ch.eitchnet.chronivaro.core.notification.ChronivaroNotificationHelper;
import ch.eitchnet.chronivaro.core.notification.Notification;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringResult;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResultState;
import li.strolch.utils.dbc.DBC;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.initVersion;
import static java.text.MessageFormat.format;

public class RequestAbsenceService extends AbstractChronivaroService<RequestAbsenceService.RequestAbsenceArgument,
		StringResult> {

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
		List<Notification> notifications = List.of();
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			// Check if actor is acting on behalf of another employee
			Optional<Resource> userEmployeeO = ChronivaroModelHelper.findEmployeeByUser(tx,
					tx.getCertificate().getUserId());
			String employeeName = userEmployeeO.map(e -> e.getString(PARAM_PERSONAL_NUMBER)).orElse(arg.employeeId);
			boolean isSelf = userEmployeeO.isPresent() && userEmployeeO.get().getId().equals(arg.employeeId);
			if (!isSelf)
				ChronivaroModelHelper.assertCanManageEmployee(tx, arg.employeeId);

			boolean isApproved = arg.directApprove || STATE_APPROVED.equalsIgnoreCase(arg.state);
			if (isApproved && isSelf && !ChronivaroModelHelper.isAdmin(tx)) {
				// Employees cannot directly self-approve absences
				throw new IllegalArgumentException("Employees cannot self-approve absence requests.");
			}

			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, arg.employeeId, true);
			ZoneId tz = ChronivaroModelHelper.getEmployeeTimezone(employee);

			LocalDate startDate = arg.start.toLocalDate();
			LocalDate endDate = arg.end.toLocalDate();

			PeriodHelper.assertPeriodOpen(tx, arg.employeeId, startDate);
			if (!startDate.equals(endDate)) {
				PeriodHelper.assertPeriodOpen(tx, arg.employeeId, endDate);
			}

			Resource absenceType = AbsenceHelper.getAbsenceType(tx, arg.absenceTypeCode);
			AbsenceHelper.validateDurationType(absenceType, arg.durationType);

			ZonedDateTime startZdt = startDate.atStartOfDay(tz);
			ZonedDateTime endZdt = endDate.atTime(23, 59, 59, 999_000_000).atZone(tz);

			boolean isDraft = !isApproved && (arg.asDraft || STATE_DRAFT.equalsIgnoreCase(arg.state));
			if (!isDraft) {
				AbsenceHelper.validateCommentRequired(absenceType, arg.comment);
				AbsenceHelper.validateNoOverlap(tx, arg.employeeId, startZdt, endZdt, null);
			}

			Resource absence = tx.getResourceTemplate(TYPE_ABSENCE, true);
			absence.setName("Absence " + startDate);

			absence.setRelation(PARAM_EMPLOYEE, employee);
			absence.setRelation(PARAM_ABSENCE_TYPE, absenceType);
			absence.setDate(PARAM_START, startZdt);
			absence.setDate(PARAM_END, endZdt);
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
					int totalMinutes = AbsenceHelper.getVacationMinutes(tx, arg.employeeId, absence);

					if (totalMinutes > 0) {
						VacationHelper.assertSufficientVacationBalance(tx, arg.employeeId, totalMinutes,
								absence.getDate(PARAM_START));

						Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
						entry.setName("Vacation Usage " + absence.getName());

						entry.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, arg.employeeId, true));
						entry.setRelation(PARAM_ABSENCE, absence);
						entry.setDate(PARAM_DATE, absence.getDate(PARAM_START));
						entry.setDate(PARAM_CREATED_AT, java.time.ZonedDateTime.now());
						entry.setString(PARAM_VACATION_TYPE, VACATION_USAGE);
						entry.setInteger(PARAM_VALUE, -totalMinutes);
						entry.setString(PARAM_COMMENT, "Vacation usage for absence " + absence.getName());
						entry.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());

						initVersion(entry, tx);
						tx.add(entry);
						ChronivaroAuditHelper.audit(tx, TYPE_VACATION_ACCOUNT_ENTRY, entry.getId(), AUDIT_ACTION_CREATE,
								format("Created vacation usage entry for absence {0} ({1} minutes)", absence.getName(),
										totalMinutes));
					}
				}

				ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE, absence.getId(), AUDIT_ACTION_CREATE, arg.comment,
						format("Created and approved absence for employee {0} ({1} from {2} to {3}) by {4}",
								employeeName, arg.absenceTypeCode, arg.start, arg.end,
								tx.getCertificate().getUsername()));
			} else if (isDraft) {
				ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE, absence.getId(), AUDIT_ACTION_CREATE, arg.comment,
						format("Created draft absence for employee {0} ({1} from {2} to {3}) by {4}", employeeName,
								arg.absenceTypeCode, arg.start, arg.end, tx.getCertificate().getUsername()));
			} else {
				ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE, absence.getId(), AUDIT_ACTION_SUBMIT, arg.comment,
						format("Requested absence for employee {0} ({1} from {2} to {3}) by {4}", employeeName,
								arg.absenceTypeCode, arg.start, arg.end, tx.getCertificate().getUsername()));
			}

			if (isApproved) {
				notifications = ChronivaroNotificationHelper.absenceDecision(tx, getAgent(), arg.employeeId, startZdt,
						endZdt, "approved", tx.getCertificate().getUserId(), tx.getCertificate().getUsername());
			} else if (!isDraft) {
				notifications = ChronivaroNotificationHelper.absenceSubmitted(tx, getAgent(), arg.employeeId, startZdt,
						endZdt, tx.getCertificate().getUserId(), tx.getCertificate().getUsername());
			}

			tx.commitOnClose();
		}

		dispatchNotifications(notifications);
		return new StringResult(absenceId);
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
