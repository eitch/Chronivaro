package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.VacationAccountSummary;
import ch.eitchnet.chronivaro.core.model.VacationHelper;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

public class CalculateVacationEntitlementService extends
		AbstractService<CalculateVacationEntitlementService.CalculateVacationEntitlementArgument, CalculateVacationEntitlementService.CalculateVacationEntitlementResult> {

	public static class CalculateVacationEntitlementArgument extends ServiceArgument {
		public String employeeId;
		public Integer year;

		public CalculateVacationEntitlementArgument() {
		}
	}

	public static class CalculateVacationEntitlementResult extends ServiceResult {
		public int entitlementMinutes;
		public VacationAccountSummary summary;

		public CalculateVacationEntitlementResult(int entitlementMinutes, VacationAccountSummary summary) {
			super(ServiceResult.success().getState());
			this.entitlementMinutes = entitlementMinutes;
			this.summary = summary;
		}

		public CalculateVacationEntitlementResult() {
		}
	}

	@Override
	protected CalculateVacationEntitlementResult internalDoService(CalculateVacationEntitlementArgument arg)
			throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.employeeId);
		DBC.PRE.assertNotNull("year must be set", arg.year);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			int entitlement = VacationHelper.calculateAnnualEntitlement(tx, arg.employeeId, arg.year);
			VacationAccountSummary summary = VacationHelper.getVacationAccountSummary(tx, arg.employeeId, arg.year);
			return new CalculateVacationEntitlementResult(entitlement, summary);
		}
	}

	@Override
	public CalculateVacationEntitlementArgument getArgumentInstance() {
		return new CalculateVacationEntitlementArgument();
	}

	@Override
	public CalculateVacationEntitlementResult getResultInstance() {
		return new CalculateVacationEntitlementResult();
	}
}
