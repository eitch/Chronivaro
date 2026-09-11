package ch.eitchnet.chronivaro.core.model;

import java.time.YearMonth;
import java.util.List;

public record MonthSummary(String employeeId, YearMonth yearMonth, int totalTargetMinutes, int totalActualMinutes,
		int paidAbsenceMinutes, int unpaidAbsenceMinutes, int vacationMinutes, int totalHolidayMinutes,
		int totalAbsenceMinutes, int initialBalanceMinutes, int manualCorrectionsMinutes, int totalOnCallMinutes,
		int targetMinutesToDate, int actualMinutesToDate, int holidayMinutesToDate, int absenceMinutesToDate,
		int periodBalanceMinutes, int endBalanceMinutes,
		List<DaySummary> daySummaries) {

	public MonthSummary(String employeeId, YearMonth yearMonth, int totalTargetMinutes, int totalActualMinutes,
			int paidAbsenceMinutes, int unpaidAbsenceMinutes, int vacationMinutes, int totalHolidayMinutes,
			int totalAbsenceMinutes, int initialBalanceMinutes, int manualCorrectionsMinutes, int totalOnCallMinutes,
			List<DaySummary> daySummaries) {
		this(employeeId, yearMonth, totalTargetMinutes, totalActualMinutes, paidAbsenceMinutes, unpaidAbsenceMinutes,
				vacationMinutes, totalHolidayMinutes, totalAbsenceMinutes, initialBalanceMinutes,
				manualCorrectionsMinutes, totalOnCallMinutes,
				totalTargetMinutes, totalActualMinutes, totalHolidayMinutes, totalAbsenceMinutes,
				totalActualMinutes + totalHolidayMinutes + totalAbsenceMinutes - totalTargetMinutes,
				initialBalanceMinutes + (totalActualMinutes + totalHolidayMinutes + totalAbsenceMinutes - totalTargetMinutes) + manualCorrectionsMinutes,
				daySummaries);
	}

	public MonthSummary(String employeeId, YearMonth yearMonth, int totalTargetMinutes, int totalActualMinutes,
			int paidAbsenceMinutes, int unpaidAbsenceMinutes, int vacationMinutes, int totalHolidayMinutes,
			int totalAbsenceMinutes, int initialBalanceMinutes, int manualCorrectionsMinutes,
			List<DaySummary> daySummaries) {
		this(employeeId, yearMonth, totalTargetMinutes, totalActualMinutes, paidAbsenceMinutes, unpaidAbsenceMinutes,
				vacationMinutes, totalHolidayMinutes, totalAbsenceMinutes, initialBalanceMinutes,
				manualCorrectionsMinutes, 0, daySummaries);
	}

	public MonthSummary(String employeeId, YearMonth yearMonth, int totalTargetMinutes, int totalActualMinutes,
			int totalHolidayMinutes, int totalAbsenceMinutes, int initialBalanceMinutes,
			List<DaySummary> daySummaries) {
		this(employeeId, yearMonth, totalTargetMinutes, totalActualMinutes, totalAbsenceMinutes, 0, 0,
				totalHolidayMinutes, totalAbsenceMinutes, initialBalanceMinutes, 0, 0, daySummaries);
	}

	public int getPeriodBalance() {
		return periodBalanceMinutes;
	}

	public int getEndBalance() {
		return endBalanceMinutes;
	}

	public int getFullPeriodBalance() {
		return totalActualMinutes + totalHolidayMinutes + totalAbsenceMinutes - totalTargetMinutes;
	}

	public int getFullEndBalance() {
		return initialBalanceMinutes + getFullPeriodBalance() + manualCorrectionsMinutes;
	}

	public int getBalanceToDate() {
		return actualMinutesToDate + holidayMinutesToDate + absenceMinutesToDate - targetMinutesToDate;
	}

	public int getEndBalanceToDate() {
		return initialBalanceMinutes + getBalanceToDate() + manualCorrectionsMinutes;
	}
}
