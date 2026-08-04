package ch.atexxi.chronivaro.rest.dto;

import java.time.YearMonth;
import java.util.List;

public record MonthSummaryDto(
		String employeeId,
		YearMonth yearMonth,
		int totalTargetMinutes,
		int totalActualMinutes,
		int totalHolidayMinutes,
		int totalAbsenceMinutes,
		int initialBalanceMinutes,
		int periodBalanceMinutes,
		int endBalanceMinutes,
		List<DaySummaryDto> daySummaries
) {}
