package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.AbsenceHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.PeriodHelper;
import ch.eitchnet.chronivaro.core.model.ScheduleHelper;
import ch.eitchnet.chronivaro.core.model.VacationHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.Operation;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.base.AccessDeniedException;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

public class UpdateAbsenceService extends AbstractService<UpdateAbsenceService.UpdateAbsenceArgument, ServiceResult> {

	public static class UpdateAbsenceArgument extends ServiceArgument {
		public String absenceId;
		public String absenceTypeCode;
		public ZonedDateTime start;
		public ZonedDateTime end;
		public String durationType;
		public String dayPart;
		public Integer minutes;
		public String comment;
	}

	@Override
	protected ServiceResult internalDoService(UpdateAbsenceArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("absenceId must be set", arg.absenceId);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, arg.absenceId, true);
			tx.readLock(absence);

			String currentState = absence.getString(PARAM_STATE);
			if (!currentState.equals(STATE_DRAFT) && !currentState.equals(STATE_SUBMITTED) && !currentState.equals(STATE_APPROVED)) {
				throw new IllegalStateException("Only absences in state DRAFT, SUBMITTED or APPROVED can be updated!");
			}

			Resource employee = tx.getResourceByRelation(absence, PARAM_EMPLOYEE, true);

			PeriodHelper.assertPeriodOpen(tx, employee.getId(), absence.getDate(PARAM_START).toLocalDate());
			if (arg.start != null) {
				PeriodHelper.assertPeriodOpen(tx, employee.getId(), arg.start.toLocalDate());
			}

			boolean isAdminOrHr = tx.getPrivilegeContext().hasRole(ROLE_HR)
					|| tx.getPrivilegeContext().hasRole(ROLE_ADMIN)
					|| tx.getPrivilegeContext().hasRole(ROLE_ADMINISTRATOR)
					|| tx.getPrivilegeContext().hasRole(ROLE_STROLCH_ADMIN)
					|| tx.getPrivilegeContext().hasRole(ROLE_PRIVILEGE_ADMIN);

			if (currentState.equals(STATE_APPROVED)) {
				// Approved absences can only be corrected by HR/Admin or Supervisor with manage permission
				if (!isAdminOrHr) {
					ChronivaroModelHelper.assertCanManageEmployee(tx, employee.getId());
				}
			} else {
				// Authorization: Only own absence or Supervisor/HR/Admin
				if (!isAdminOrHr) {
					Optional<Resource> currentEmployee = ChronivaroModelHelper.findEmployeeByUser(tx, tx.getCertificate().getUserId());
					if (currentEmployee.isPresent() && currentEmployee.get().getId().equals(employee.getId())) {
						// Self-service: allowed
					} else {
						tx.assertHasPrivilege(Operation.UPDATE, employee);
					}
				}
			}

			absence = absence.getClone();

			if (arg.absenceTypeCode != null) {
				Resource absenceType = AbsenceHelper.getAbsenceType(tx, arg.absenceTypeCode);
				absence.setRelation(PARAM_ABSENCE_TYPE, absenceType);
			}

			if (arg.start != null) absence.setDate(PARAM_START, arg.start);
			if (arg.end != null) absence.setDate(PARAM_END, arg.end);
			if (arg.durationType != null) absence.setString(PARAM_DURATION_TYPE, arg.durationType);
			if (arg.dayPart != null) absence.setString(PARAM_DAY_PART, arg.dayPart);
			if (arg.minutes != null) absence.setInteger(PARAM_MINUTES, arg.minutes);
			if (arg.comment != null) absence.setString(PARAM_COMMENT, arg.comment);

			Resource finalAbsenceType = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
			AbsenceHelper.validateDurationType(finalAbsenceType, absence.getString(PARAM_DURATION_TYPE));

			if (currentState.equals(STATE_SUBMITTED) || currentState.equals(STATE_APPROVED)) {
				String finalComment = absence.hasParameter(PARAM_COMMENT) ? absence.getString(PARAM_COMMENT) : "";
				AbsenceHelper.validateCommentRequired(finalAbsenceType, finalComment);
				AbsenceHelper.validateNoOverlap(tx, employee.getId(), absence.getDate(PARAM_START), absence.getDate(PARAM_END),
						absence.getId());
			}

			bumpVersion(absence, tx);
			tx.update(absence);

			String auditAction = currentState.equals(STATE_APPROVED) ? AUDIT_ACTION_CORRECT : AUDIT_ACTION_UPDATE;
			String auditDesc = currentState.equals(STATE_APPROVED)
					? "Corrected approved absence " + absence.getId() + " for employee " + employee.getId()
					: "Updated absence " + absence.getId() + " for employee " + employee.getId();
			ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE, absence.getId(), auditAction, arg.comment, auditDesc);

			// If it was APPROVED, adjust vacation account entries
			if (currentState.equals(STATE_APPROVED)) {
				// Remove any existing vacation entries linked to this absence
				String absenceId = absence.getId();
				List<Resource> existingVacEntries = tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
						.filter(e -> e.hasRelation(PARAM_ABSENCE) && absenceId.equals(e.getRelationId(PARAM_ABSENCE)))
						.toList();
				for (Resource vacEntry : existingVacEntries) {
					tx.remove(vacEntry);
				}

				// If the absence is a vacation absence, create new vacation usage entry
				if (VacationHelper.isVacationAbsence(tx, absence)) {
					LocalDate start = absence.getDate(PARAM_START).toLocalDate();
					LocalDate end = absence.getDate(PARAM_END).toLocalDate();

					int totalMinutes = 0;
					for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
						totalMinutes += calculateMinutesForDay(tx, employee.getId(), absence, date);
					}

					if (totalMinutes > 0) {
						VacationHelper.assertSufficientVacationBalance(tx, employee.getId(), totalMinutes, absence.getDate(PARAM_START));

						Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
						entry.setName("Vacation Usage " + absence.getId());

						entry.setRelation(PARAM_EMPLOYEE, employee);
						entry.setRelation(PARAM_ABSENCE, absence);
						entry.setDate(PARAM_DATE, absence.getDate(PARAM_START));
						entry.setDate(PARAM_CREATED_AT, ZonedDateTime.now());
						entry.setString(PARAM_VACATION_TYPE, VACATION_USAGE);
						entry.setInteger(PARAM_VALUE, -totalMinutes);
						entry.setString(PARAM_COMMENT, "Vacation usage for corrected absence " + absence.getId());
						entry.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());

						initVersion(entry, tx);
						tx.add(entry);
						ChronivaroAuditHelper.audit(tx, TYPE_VACATION_ACCOUNT_ENTRY, entry.getId(), AUDIT_ACTION_CREATE,
								"Created vacation usage entry for corrected absence " + absence.getId() + " (" + totalMinutes + " minutes)");
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
	public UpdateAbsenceArgument getArgumentInstance() {
		return new UpdateAbsenceArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
