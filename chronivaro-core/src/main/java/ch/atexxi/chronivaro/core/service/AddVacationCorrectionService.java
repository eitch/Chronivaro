package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class AddVacationCorrectionService
		extends AbstractService<AddVacationCorrectionService.AddVacationCorrectionArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(AddVacationCorrectionArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.employeeId);
		DBC.PRE.assertNotNull("value must be set", arg.value);
		DBC.PRE.assertNotEmpty("comment must be set", arg.comment);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource employee = ChronivaroModelHelper.getEmployee(tx, arg.employeeId);

			Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
			entry.setName("Vacation Correction " + arg.employeeId);

			ZonedDateTime now = ZonedDateTime.now(ChronivaroModelHelper.getEmployeeTimezone(employee));

			entry.setRelation(PARAM_EMPLOYEE, employee);
			entry.setString(PARAM_VACATION_TYPE, VACATION_CORRECTION);
			entry.setDate(PARAM_DATE, now);
			entry.setInteger(PARAM_VALUE, arg.value);
			entry.setString(PARAM_COMMENT, arg.comment);
			entry.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());

			tx.add(entry);

			ChronivaroAuditHelper.audit(tx, TYPE_VACATION_ACCOUNT_ENTRY, entry.getId(), PARAM_VALUE, null,
					String.valueOf(arg.value));

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
	}
}
