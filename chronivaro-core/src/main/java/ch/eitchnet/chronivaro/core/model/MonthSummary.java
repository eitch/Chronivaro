package ch.eitchnet.chronivaro.core.model;

import java.time.YearMonth;
import java.util.List;

public record MonthSummary(String employeeId, YearMonth yearMonth, int totalTargetMinutes, int totalActualMinutes,
		int paidAbsenceMinutes, int unpaidAbsenceMinutes, int vacationMinutes, int totalHolidayMinutes,
		int totalAbsenceMinutes, int initialBalanceMinutes, int manualCorrectionsMinutes,
		List<DaySummary> daySummaries) {

	public MonthSummary(String employeeId, YearMonth yearMonth, int totalTargetMinutes, int totalActualMinutes,
			int totalHolidayMinutes, int totalAbsenceMinutes, int initialBalanceMinutes,
			List<DaySummary> daySummaries) {
		this(employeeId, yearMonth, totalTargetMinutes, totalActualMinutes, totalAbsenceMinutes, 0, 0,
				totalHolidayMinutes, totalAbsenceMinutes, initialBalanceMinutes, 0, daySummaries);
	}

	public int getPeriodBalance() {
		return totalActualMinutes + totalHolidayMinutes + totalAbsenceMinutes - totalTargetMinutes;
	}

	public int getEndBalance() {
		return initialBalanceMinutes + getPeriodBalance() + manualCorrectionsMinutes;
	}
}
