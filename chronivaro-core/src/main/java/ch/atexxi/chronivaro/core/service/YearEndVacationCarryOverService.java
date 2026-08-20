package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.VacationAccountSummary;
import ch.atexxi.chronivaro.core.model.VacationHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.service.api.ServiceResultState;
import li.strolch.utils.dbc.DBC;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.initVersion;
import static li.strolch.utils.helper.StringHelper.isNotEmpty;

public class YearEndVacationCarryOverService
		extends AbstractService<YearEndVacationCarryOverService.YearEndVacationCarryOverArgument, YearEndVacationCarryOverService.YearEndVacationCarryOverResult> {

	public static class YearEndVacationCarryOverArgument extends ServiceArgument {
		public String employeeId;
		public Integer sourceYear;
		public Integer targetYear;
		public boolean force;

		public YearEndVacationCarryOverArgument() {
		}

		public YearEndVacationCarryOverArgument(String employeeId, Integer sourceYear) {
			this.employeeId = employeeId;
			this.sourceYear = sourceYear;
		}

		public YearEndVacationCarryOverArgument(String employeeId, Integer sourceYear, Integer targetYear) {
			this.employeeId = employeeId;
			this.sourceYear = sourceYear;
			this.targetYear = targetYear;
		}
	}

	public static class YearEndVacationCarryOverResult extends ServiceResult {
		public int processedEmployeesCount;
		public int totalCarryOverMinutes;
		public List<String> createdEntryIds = new ArrayList<>();

		public YearEndVacationCarryOverResult() {
			super(ServiceResultState.SUCCESS);
		}

		public YearEndVacationCarryOverResult(ServiceResultState state) {
			super(state);
		}
	}

	@Override
	protected YearEndVacationCarryOverResult internalDoService(YearEndVacationCarryOverArgument arg) throws Exception {
		int srcYear;
		int tgtYear;
		if (arg.sourceYear != null) {
			srcYear = arg.sourceYear;
			tgtYear = arg.targetYear != null ? arg.targetYear : srcYear + 1;
		} else if (arg.targetYear != null) {
			tgtYear = arg.targetYear;
			srcYear = tgtYear - 1;
		} else {
			throw new IllegalArgumentException("sourceYear or targetYear must be set");
		}

		YearEndVacationCarryOverResult result = new YearEndVacationCarryOverResult();

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			List<Resource> employees;
			if (isNotEmpty(arg.employeeId)) {
				Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, arg.employeeId, true);
				if (!tx.getPrivilegeContext().hasRole(ROLE_HR) && !tx.getPrivilegeContext().hasRole(ROLE_ADMIN)) {
					ChronivaroModelHelper.assertCanManageEmployee(tx, arg.employeeId);
				}
				employees = List.of(employee);
			} else {
				employees = tx.streamResources(TYPE_EMPLOYEE)
						.filter(e -> e.getBoolean(PARAM_ACTIVE))
						.toList();
			}

			LocalDate srcYearEnd = LocalDate.of(srcYear, 12, 31);
			LocalDate srcYearStart = LocalDate.of(srcYear, 1, 1);

			for (Resource emp : employees) {
				LocalDate joinDate = ChronivaroModelHelper.getJoinDate(emp);
				if (joinDate.isAfter(srcYearEnd)) {
					continue;
				}

				Optional<LocalDate> exitDateOpt = ChronivaroModelHelper.getExitDate(emp);
				if (exitDateOpt.isPresent() && exitDateOpt.get().isBefore(srcYearStart)) {
					continue;
				}

				VacationAccountSummary summary = VacationHelper.getVacationAccountSummary(tx, emp.getId(), srcYear);
				int remainingMinutes = summary.remainingMinutes();

				Optional<Resource> existingCarryOver = VacationHelper.findCarryOverEntry(tx, emp.getId(), tgtYear);
				String empName = emp.hasParameter(PARAM_FIRSTNAME) && emp.hasParameter(PARAM_LASTNAME)
						? emp.getString(PARAM_FIRSTNAME) + " " + emp.getString(PARAM_LASTNAME)
						: emp.getName();

				if (existingCarryOver.isPresent()) {
					if (arg.force) {
						int currentCarryOver = existingCarryOver.get().getInteger(PARAM_VALUE);
						int delta = remainingMinutes - currentCarryOver;
						if (delta != 0) {
							Resource corr = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
							corr.setName("Carry-Over Adjustment " + tgtYear + " (" + empName + ")");
							corr.setString(PARAM_VACATION_TYPE, VACATION_CORRECTION);
							corr.setDate(PARAM_DATE, LocalDate.of(tgtYear, 1, 1).atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(emp)));
							corr.setInteger(PARAM_VALUE, delta);
							corr.setString(PARAM_COMMENT, "Carry-over adjustment from " + srcYear + " to " + tgtYear);
							corr.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());
							corr.setRelation(PARAM_EMPLOYEE, emp);

							initVersion(corr, tx);
							tx.add(corr);
							result.createdEntryIds.add(corr.getId());
							ChronivaroAuditHelper.audit(tx, TYPE_VACATION_ACCOUNT_ENTRY, corr.getId(), AUDIT_ACTION_CREATE,
									"Created carry-over adjustment entry for year " + tgtYear + " (delta: " + delta + " minutes from " + srcYear + ")");
						}
					}
				} else {
					if (remainingMinutes != 0) {
						Resource entry = tx.getResourceTemplate(TYPE_VACATION_ACCOUNT_ENTRY, true);
						entry.setName("Vacation Carry-Over " + tgtYear + " (" + empName + ")");
						entry.setString(PARAM_VACATION_TYPE, VACATION_CARRY_OVER);
						entry.setDate(PARAM_DATE, LocalDate.of(tgtYear, 1, 1).atStartOfDay(ChronivaroModelHelper.getEmployeeTimezone(emp)));
						entry.setInteger(PARAM_VALUE, remainingMinutes);
						entry.setString(PARAM_COMMENT, "Vacation carry-over from " + srcYear + " to " + tgtYear);
						entry.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());
						entry.setRelation(PARAM_EMPLOYEE, emp);

						initVersion(entry, tx);
						tx.add(entry);
						result.createdEntryIds.add(entry.getId());
						result.totalCarryOverMinutes += remainingMinutes;

						ChronivaroAuditHelper.audit(tx, TYPE_VACATION_ACCOUNT_ENTRY, entry.getId(), AUDIT_ACTION_CREATE,
								"Created vacation carry-over for year " + tgtYear + " (" + remainingMinutes + " minutes from " + srcYear + ")");
					}
				}

				result.processedEmployeesCount++;
			}

			tx.commitOnClose();
		}

		return result;
	}

	@Override
	public YearEndVacationCarryOverArgument getArgumentInstance() {
		return new YearEndVacationCarryOverArgument();
	}

	@Override
	public YearEndVacationCarryOverResult getResultInstance() {
		return new YearEndVacationCarryOverResult();
	}
}
