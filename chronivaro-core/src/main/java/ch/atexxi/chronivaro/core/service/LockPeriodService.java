package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class LockPeriodService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("periodId must be set", arg.value);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, arg.value, true).getClone();

			String currentState = period.getString(PARAM_STATE);
			if (!currentState.equals(STATE_APPROVED)) {
				throw new IllegalStateException("Period is not in state APPROVED!");
			}

			period.setString(PARAM_STATE, STATE_LOCKED);
			tx.update(period);

			ChronivaroAuditHelper.audit(tx, TYPE_TIME_PERIOD, period.getId(), PARAM_STATE, currentState, STATE_LOCKED);

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
