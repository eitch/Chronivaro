package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.VacationHelper;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.util.Optional;

public class CreditVacationEntitlementService
		extends AbstractService<CreditVacationEntitlementService.CreditVacationEntitlementArgument, CreditVacationEntitlementService.CreditVacationEntitlementResult> {

	public static class CreditVacationEntitlementArgument extends ServiceArgument {
		public String employeeId;
		public Integer year;
		public boolean forceRecalculate;

		public CreditVacationEntitlementArgument() {
		}

		public CreditVacationEntitlementArgument(String employeeId, Integer year, boolean forceRecalculate) {
			this.employeeId = employeeId;
			this.year = year;
			this.forceRecalculate = forceRecalculate;
		}
	}

	public static class CreditVacationEntitlementResult extends ServiceResult {
		public int entitlementMinutes;
		public String entryId;

		public CreditVacationEntitlementResult(int entitlementMinutes, String entryId) {
			super(ServiceResult.success().getState());
			this.entitlementMinutes = entitlementMinutes;
			this.entryId = entryId;
		}

		public CreditVacationEntitlementResult() {
		}
	}

	@Override
	protected CreditVacationEntitlementResult internalDoService(CreditVacationEntitlementArgument arg)
			throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.employeeId);
		DBC.PRE.assertNotNull("year must be set", arg.year);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			int entitlementMinutes = VacationHelper.calculateAnnualEntitlement(tx, arg.employeeId, arg.year);
			Optional<String> entryId = VacationHelper.creditOrRecalculateEntitlement(tx, arg.employeeId, arg.year,
					arg.forceRecalculate);
			tx.commitOnClose();
			return new CreditVacationEntitlementResult(entitlementMinutes, entryId.orElse(null));
		}
	}

	@Override
	public CreditVacationEntitlementArgument getArgumentInstance() {
		return new CreditVacationEntitlementArgument();
	}

	@Override
	public CreditVacationEntitlementResult getResultInstance() {
		return new CreditVacationEntitlementResult();
	}
}
