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
		int totalOnCallMinutes,
		int targetMinutesToDate,
		int actualMinutesToDate,
		int holidayMinutesToDate,
		int absenceMinutesToDate,
		int balanceToDateMinutes,
		int endBalanceToDateMinutes,
		int fullPeriodBalanceMinutes,
		int fullEndBalanceMinutes,
		int endBalanceMinutes,
		List<DaySummaryDto> daySummaries
) {
	public MonthSummaryDto(String employeeId, YearMonth yearMonth, int totalTargetMinutes, int totalActualMinutes,
			int paidAbsenceMinutes, int unpaidAbsenceMinutes, int vacationMinutes, int totalHolidayMinutes,
			int totalAbsenceMinutes, int initialBalanceMinutes, int periodBalanceMinutes,
			int manualCorrectionsMinutes, int totalOnCallMinutes, int endBalanceMinutes,
			List<DaySummaryDto> daySummaries) {
		this(employeeId, yearMonth, totalTargetMinutes, totalActualMinutes, paidAbsenceMinutes, unpaidAbsenceMinutes,
				vacationMinutes, totalHolidayMinutes, totalAbsenceMinutes, initialBalanceMinutes, periodBalanceMinutes,
				manualCorrectionsMinutes, totalOnCallMinutes,
				totalTargetMinutes, totalActualMinutes, totalHolidayMinutes, totalAbsenceMinutes,
				periodBalanceMinutes, endBalanceMinutes,
				totalActualMinutes + totalHolidayMinutes + totalAbsenceMinutes - totalTargetMinutes,
				initialBalanceMinutes + (totalActualMinutes + totalHolidayMinutes + totalAbsenceMinutes - totalTargetMinutes) + manualCorrectionsMinutes,
				endBalanceMinutes, daySummaries);
	}

	public MonthSummaryDto(String employeeId, YearMonth yearMonth, int totalTargetMinutes, int totalActualMinutes,
			int paidAbsenceMinutes, int unpaidAbsenceMinutes, int vacationMinutes, int totalHolidayMinutes,
			int totalAbsenceMinutes, int initialBalanceMinutes, int periodBalanceMinutes,
			int manualCorrectionsMinutes, int endBalanceMinutes, List<DaySummaryDto> daySummaries) {
		this(employeeId, yearMonth, totalTargetMinutes, totalActualMinutes, paidAbsenceMinutes, unpaidAbsenceMinutes,
				vacationMinutes, totalHolidayMinutes, totalAbsenceMinutes, initialBalanceMinutes, periodBalanceMinutes,
				manualCorrectionsMinutes, 0, endBalanceMinutes, daySummaries);
	}

	public MonthSummaryDto(String employeeId, YearMonth yearMonth, int totalTargetMinutes, int totalActualMinutes,
			int totalHolidayMinutes, int totalAbsenceMinutes, int initialBalanceMinutes,
			int periodBalanceMinutes, int endBalanceMinutes, List<DaySummaryDto> daySummaries) {
		this(employeeId, yearMonth, totalTargetMinutes, totalActualMinutes, totalAbsenceMinutes, 0, 0,
				totalHolidayMinutes, totalAbsenceMinutes, initialBalanceMinutes, periodBalanceMinutes, 0, 0,
				endBalanceMinutes, daySummaries);
	}
}
