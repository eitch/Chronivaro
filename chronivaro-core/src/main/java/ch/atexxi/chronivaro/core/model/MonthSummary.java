package ch.atexxi.chronivaro.core.model;

import java.time.YearMonth;
import java.util.List;

public record MonthSummary(
		String employeeId,
		YearMonth yearMonth,
		int totalTargetMinutes,
		int totalActualMinutes,
		int totalHolidayMinutes,
		int totalAbsenceMinutes,
		int initialBalanceMinutes,
		List<DaySummary> daySummaries
) {
	public int getPeriodBalance() {
		return totalActualMinutes + totalHolidayMinutes + totalAbsenceMinutes - totalTargetMinutes;
	}

	public int getEndBalance() {
		return initialBalanceMinutes + getPeriodBalance();
	}
}
