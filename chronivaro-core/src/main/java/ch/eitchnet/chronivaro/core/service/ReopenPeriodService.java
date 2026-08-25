package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.PeriodHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.YearMonth;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;
import static li.strolch.utils.helper.StringHelper.isNotEmpty;

public class ReopenPeriodService extends AbstractService<PeriodActionArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(PeriodActionArgument arg) throws Exception {
		DBC.PRE.assertNotNull("Argument must be set", arg);
		DBC.PRE.assertNotEmpty("Reopening reason must be provided in comment", arg.comment);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource period;
			if (isNotEmpty(arg.periodId)) {
				period = tx.getResourceBy(TYPE_TIME_PERIOD, arg.periodId, true).getClone();
			} else if (isNotEmpty(arg.employeeId) && arg.yearMonth != null) {
				period = PeriodHelper.getPeriod(tx, arg.employeeId, arg.yearMonth, true).getClone();
			} else {
				throw new IllegalArgumentException("Either periodId or (employeeId and yearMonth) must be provided!");
			}

			String currentState = period.getString(PARAM_STATE);
			if (currentState.equals(STATE_OPEN)) {
				throw new IllegalStateException("Period is already OPEN!");
			}

			String employeeId = period.getRelationId(PARAM_EMPLOYEE);
			YearMonth ym = YearMonth.parse(period.getString(PARAM_YEAR_MONTH));

			period.setString(PARAM_STATE, STATE_OPEN);
			period.setString(PARAM_COMMENT, arg.comment);

			String snapshot = PeriodHelper.createCalculationSnapshot(tx, employeeId, ym);
			period.setString(PARAM_CALCULATION_SNAPSHOT, snapshot);

			bumpVersion(period, tx);
			tx.update(period);

			ChronivaroAuditHelper.audit(tx, TYPE_TIME_PERIOD, period.getId(), AUDIT_ACTION_REOPEN, arg.comment,
					"Reopened time period " + period.getId() + " for employee " + employeeId);

			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	@Override
	public PeriodActionArgument getArgumentInstance() {
		return new PeriodActionArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
