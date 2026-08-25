package ch.eitchnet.chronivaro.core.report;

import java.time.YearMonth;
import java.util.List;

public record TeamReport(String teamId, String teamName, YearMonth yearMonth, List<TeamEmployeeSummary> employeeSummaries) {

	public record TeamEmployeeSummary(String employeeId, String employeeName, String teamId, YearMonth yearMonth,
									  int targetMinutes, int actualMinutes, int holidayMinutes, int absenceMinutes,
									  int initialBalanceMinutes, int periodBalanceMinutes, int endBalanceMinutes,
									  String periodState, int missingBookingsCount) {
	}
}
