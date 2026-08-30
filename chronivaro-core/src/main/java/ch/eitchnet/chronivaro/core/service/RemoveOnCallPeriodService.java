package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;

public class RemoveOnCallPeriodService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("id is required", arg.value);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource onCallPeriod = tx.getResourceBy(TYPE_ON_CALL_PERIOD, arg.value, true);
			tx.readLock(onCallPeriod);

			String employeeId = onCallPeriod.getRelationId(PARAM_EMPLOYEE);
			ChronivaroModelHelper.assertCanManageEmployee(tx, employeeId);

			tx.remove(onCallPeriod);

			ChronivaroAuditHelper.audit(tx, TYPE_ON_CALL_PERIOD, arg.value, AUDIT_ACTION_REMOVE,
					"Removed on-call period " + arg.value + " for employee " + employeeId);

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
