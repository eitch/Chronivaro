package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.PeriodHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.YearMonth;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;
import static java.text.MessageFormat.format;
import static li.strolch.utils.helper.StringHelper.isNotEmpty;

public class LockPeriodService extends AbstractService<PeriodActionArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(PeriodActionArgument arg) throws Exception {
		DBC.PRE.assertNotNull("Argument must be set", arg);

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
			if (!currentState.equals(STATE_APPROVED)) {
				throw new IllegalStateException(
						"Period is in state " + currentState + ", but only APPROVED periods can be locked!");
			}

			String employeeId = period.getRelationId(PARAM_EMPLOYEE);
			Resource employee = ChronivaroModelHelper.getEmployee(tx, employeeId);
			YearMonth ym = YearMonth.parse(period.getString(PARAM_YEAR_MONTH));

			period.setString(PARAM_STATE, STATE_LOCKED);
			if (arg.comment != null)
				period.setString(PARAM_COMMENT, arg.comment);

			String snapshot = PeriodHelper.createCalculationSnapshot(tx, employeeId, ym);
			period.setString(PARAM_CALCULATION_SNAPSHOT, snapshot);

			bumpVersion(period, tx);
			tx.update(period);

			ChronivaroAuditHelper.audit(tx, TYPE_TIME_PERIOD, period.getId(), AUDIT_ACTION_LOCK, arg.comment,
					format("Locked time period {0} for employee {1}", period.getName(),
							employee.getString(PARAM_PERSONAL_NUMBER)));

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
