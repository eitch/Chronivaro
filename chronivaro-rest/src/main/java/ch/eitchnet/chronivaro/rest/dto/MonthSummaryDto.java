package ch.eitchnet.chronivaro.rest.dto;

import java.time.YearMonth;
import java.util.List;

public record MonthSummaryDto(
		String employeeId,
		YearMonth yearMonth,
		int totalTargetMinutes,
		int totalActualMinutes,
		int paidAbsenceMinutes,
		int unpaidAbsenceMinutes,
		int vacationMinutes,
		int totalHolidayMinutes,
		int totalAbsenceMinutes,
		int initialBalanceMinutes,
		int periodBalanceMinutes,
		int manualCorrectionsMinutes,
		int endBalanceMinutes,
		List<DaySummaryDto> daySummaries
) {
	public MonthSummaryDto(String employeeId, YearMonth yearMonth, int totalTargetMinutes, int totalActualMinutes,
			int totalHolidayMinutes, int totalAbsenceMinutes, int initialBalanceMinutes,
			int periodBalanceMinutes, int endBalanceMinutes, List<DaySummaryDto> daySummaries) {
		this(employeeId, yearMonth, totalTargetMinutes, totalActualMinutes, totalAbsenceMinutes, 0, 0,
				totalHolidayMinutes, totalAbsenceMinutes, initialBalanceMinutes, periodBalanceMinutes, 0,
				endBalanceMinutes, daySummaries);
	}
}
