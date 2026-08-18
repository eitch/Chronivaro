package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.AbsenceHelper;
import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.Operation;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;

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

			if (!absence.getString(PARAM_STATE).equals(STATE_SUBMITTED)) {
				throw new IllegalStateException("Only absences in state SUBMITTED can be updated!");
			}

			Resource employee = tx.getResourceByRelation(absence, PARAM_EMPLOYEE, true);
			
			// Authorization: Only own absence or Supervisor/HR/Admin
			if (!tx.getPrivilegeContext().hasRole(ROLE_HR) && !tx.getPrivilegeContext().hasRole(ROLE_ADMIN)) {
				Optional<Resource> currentEmployee = ChronivaroModelHelper.findEmployeeByUser(tx, tx.getCertificate().getUserId());
				if (currentEmployee.isPresent() && currentEmployee.get().getId().equals(employee.getId())) {
					// Self-service: allowed
				} else {
					tx.assertHasPrivilege(Operation.UPDATE, employee);
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

			bumpVersion(absence, tx);
			tx.update(absence);
			tx.commitOnClose();
		}

		return ServiceResult.success();
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
