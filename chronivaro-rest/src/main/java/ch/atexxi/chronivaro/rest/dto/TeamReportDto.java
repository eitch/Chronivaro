package ch.atexxi.chronivaro.rest.dto;

import java.util.List;

public record TeamReportDto(String teamId, String teamName, String yearMonth, List<TeamEmployeeSummaryDto> employees) {

	public record TeamEmployeeSummaryDto(String employeeId, String employeeName, String teamId, String yearMonth,
										 int targetMinutes, int actualMinutes, int holidayMinutes, int absenceMinutes,
										 int initialBalanceMinutes, int periodBalanceMinutes, int endBalanceMinutes,
										 String periodState, int missingBookingsCount) {
	}
}
