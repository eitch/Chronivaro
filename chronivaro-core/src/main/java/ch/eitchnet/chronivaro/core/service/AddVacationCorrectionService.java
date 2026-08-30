package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.VacationHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

public class AddVacationCorrectionService
		extends AbstractService<AddVacationCorrectionService.AddVacationCorrectionArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(AddVacationCorrectionArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.employeeId);
		DBC.PRE.assertNotNull("value must be set", arg.value);
		DBC.PRE.assertNotEmpty("comment must be set", arg.comment);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			if (!tx.getPrivilegeContext().hasRole(ROLE_HR)
					&& !tx.getPrivilegeContext().hasRole(ROLE_ADMIN)
					&& !tx.getPrivilegeContext().hasRole(ROLE_ADMINISTRATOR)) {
				ChronivaroModelHelper.assertCanManageEmployee(tx, arg.employeeId);
			}

			Resource employee = ChronivaroModelHelper.getEmployee(tx, arg.employeeId);

			Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
			entry.setName("Vacation Correction " + arg.employeeId);

			ZonedDateTime entryDate = arg.date != null ? arg.date :
					ZonedDateTime.now(ChronivaroModelHelper.getEmployeeTimezone(employee));

			if (arg.value < 0) {
				int requestedDeduction = Math.abs(arg.value);
				VacationHelper.assertSufficientVacationBalance(tx, arg.employeeId, requestedDeduction, entryDate);
				int currentBalance = VacationHelper.getVacationBalance(tx, arg.employeeId);
				if (currentBalance < requestedDeduction) {
					throw new IllegalStateException("Insufficient vacation balance for employee " + arg.employeeId
							+ ": current balance is " + currentBalance + " minutes, but correction is " + arg.value + " minutes.");
				}
			}

			entry.setRelation(PARAM_EMPLOYEE, employee);
			entry.setString(PARAM_VACATION_TYPE, VACATION_CORRECTION);
			entry.setDate(PARAM_DATE, entryDate);
			entry.setDate(PARAM_CREATED_AT, ZonedDateTime.now(ChronivaroModelHelper.getEmployeeTimezone(employee)));
			entry.setInteger(PARAM_VALUE, arg.value);
			entry.setString(PARAM_COMMENT, arg.comment);
			entry.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());

			initVersion(entry, tx);
			tx.add(entry);

			ChronivaroAuditHelper.audit(tx, TYPE_VACATION_ACCOUNT_ENTRY, entry.getId(), AUDIT_ACTION_CREATE, arg.comment,
					"Added vacation correction of " + arg.value + " minutes for employee " + arg.employeeId);

			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	@Override
	public AddVacationCorrectionArgument getArgumentInstance() {
		return new AddVacationCorrectionArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	public static class AddVacationCorrectionArgument extends ServiceArgument {
		public String employeeId;
		public Integer value;
		public String comment;
		public ZonedDateTime date;
	}
}
