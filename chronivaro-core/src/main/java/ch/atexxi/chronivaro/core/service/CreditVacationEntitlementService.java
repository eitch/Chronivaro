package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.VacationHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.LocalDate;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

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
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, arg.employeeId, true);
			tx.readLock(employee);

			int entitlementMinutes = VacationHelper.calculateAnnualEntitlement(tx, arg.employeeId, arg.year);
			Optional<Resource> existing = VacationHelper.findEntitlementEntry(tx, arg.employeeId, arg.year);

			String entryId;
			if (existing.isPresent()) {
				Resource entry = existing.get();
				entryId = entry.getId();
				if (arg.forceRecalculate) {
					int currentCredited = entry.getInteger(PARAM_VALUE)
							+ VacationHelper.getEntitlementAdjustmentCorrections(tx, arg.employeeId, arg.year);
					int delta = entitlementMinutes - currentCredited;
					if (delta != 0) {
						String empName = employee.hasParameter(PARAM_FIRSTNAME) && employee.hasParameter(PARAM_LASTNAME)
								? employee.getString(PARAM_FIRSTNAME) + " " + employee.getString(PARAM_LASTNAME)
								: employee.getName();
						Resource corr = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
						corr.setName("Vacation Entitlement Recalculation " + arg.year + " (" + empName + ")");
						corr.setRelation(PARAM_EMPLOYEE, employee);
						corr.setString(PARAM_VACATION_TYPE, VACATION_CORRECTION);
						LocalDate joinDate = ChronivaroModelHelper.getJoinDate(employee);
						LocalDate creditDate = joinDate.isAfter(LocalDate.of(arg.year, 1, 1)) ? joinDate : LocalDate.of(arg.year, 1, 1);
						corr.setDate(PARAM_DATE, creditDate.atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(employee)));
						corr.setInteger(PARAM_VALUE, delta);
						corr.setString(PARAM_COMMENT, "Recalculated vacation entitlement adjustment for year " + arg.year
								+ " (" + (delta > 0 ? "+" + delta : String.valueOf(delta)) + " minutes)");
						corr.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());

						initVersion(corr, tx);
						tx.add(corr);
						entryId = corr.getId();

						ChronivaroAuditHelper.audit(tx, TYPE_VACATION_ACCOUNT_ENTRY, corr.getId(), AUDIT_ACTION_CREATE,
								"Recalculated vacation entitlement adjustment for year " + arg.year + " from "
										+ currentCredited + " to " + entitlementMinutes + " minutes (delta: " + delta + ")");
					}
				}
			} else {
				Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
				String empName = employee.hasParameter(PARAM_FIRSTNAME) && employee.hasParameter(PARAM_LASTNAME)
						? employee.getString(PARAM_FIRSTNAME) + " " + employee.getString(PARAM_LASTNAME)
						: employee.getName();
				entry.setName("Vacation Entitlement " + arg.year + " (" + empName + ")");
				entry.setRelation(PARAM_EMPLOYEE, employee);
				entry.setString(PARAM_VACATION_TYPE, VACATION_ENTITLEMENT);
				LocalDate joinDate = ChronivaroModelHelper.getJoinDate(employee);
				LocalDate creditDate = joinDate.isAfter(LocalDate.of(arg.year, 1, 1)) ? joinDate : LocalDate.of(arg.year, 1, 1);
				entry.setDate(PARAM_DATE, creditDate.atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(employee)));
				entry.setInteger(PARAM_VALUE, entitlementMinutes);
				entry.setString(PARAM_COMMENT, "Annual vacation entitlement " + arg.year);
				entry.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());

				initVersion(entry, tx);
				tx.add(entry);
				entryId = entry.getId();

				ChronivaroAuditHelper.audit(tx, TYPE_VACATION_ACCOUNT_ENTRY, entry.getId(), AUDIT_ACTION_CREATE,
						"Credited annual vacation entitlement for year " + arg.year + " (" + entitlementMinutes + " minutes)");
			}

			tx.commitOnClose();
			return new CreditVacationEntitlementResult(entitlementMinutes, entryId);
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
