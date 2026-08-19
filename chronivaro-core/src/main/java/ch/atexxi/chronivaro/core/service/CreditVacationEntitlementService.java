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
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;
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
					tx.readLock(entry);
					int oldVal = entry.getInteger(PARAM_VALUE);
					if (oldVal != entitlementMinutes) {
						entry = entry.getClone();
						entry.setInteger(PARAM_VALUE, entitlementMinutes);
						bumpVersion(entry, tx);
						tx.update(entry);
						ChronivaroAuditHelper.audit(tx, TYPE_VACATION_ACCOUNT_ENTRY, entry.getId(), AUDIT_ACTION_UPDATE,
								"Recalculated vacation entitlement for year " + arg.year + " from " + oldVal
										+ " to " + entitlementMinutes + " minutes");
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
