package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.VacationAccountSummary;
import ch.eitchnet.chronivaro.core.model.VacationHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.LocalDate;
import java.util.List;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.TYPE_EMPLOYEE;

public class GetVacationAccountSummaryService extends
		AbstractService<GetVacationAccountSummaryService.GetVacationAccountSummaryArgument, GetVacationAccountSummaryService.GetVacationAccountSummaryResult> {

	public static class GetVacationAccountSummaryArgument extends ServiceArgument {
		public String employeeId;
		public Integer year;

		public GetVacationAccountSummaryArgument() {
		}

		public GetVacationAccountSummaryArgument(String employeeId, Integer year) {
			this.employeeId = employeeId;
			this.year = year;
		}
	}

	public static class GetVacationAccountSummaryResult extends ServiceResult {
		public VacationAccountSummary summary;
		public List<Resource> entries;

		public GetVacationAccountSummaryResult(VacationAccountSummary summary, List<Resource> entries) {
			super(ServiceResult.success().getState());
			this.summary = summary;
			this.entries = entries;
		}

		public GetVacationAccountSummaryResult() {
		}
	}

	@Override
	protected GetVacationAccountSummaryResult internalDoService(GetVacationAccountSummaryArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.employeeId);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, arg.employeeId, true);
			int targetYear = arg.year != null ? arg.year : LocalDate.now(ChronivaroModelHelper.getEmployeeTimezone(employee)).getYear();

			VacationAccountSummary summary = VacationHelper.getVacationAccountSummary(tx, arg.employeeId, targetYear);
			List<Resource> entries = VacationHelper.getVacationEntries(tx, arg.employeeId, targetYear);

			return new GetVacationAccountSummaryResult(summary, entries);
		}
	}

	@Override
	public GetVacationAccountSummaryArgument getArgumentInstance() {
		return new GetVacationAccountSummaryArgument();
	}

	@Override
	public GetVacationAccountSummaryResult getResultInstance() {
		return new GetVacationAccountSummaryResult();
	}
}
