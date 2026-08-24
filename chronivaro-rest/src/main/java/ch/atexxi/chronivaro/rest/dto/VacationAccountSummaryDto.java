package ch.atexxi.chronivaro.rest.dto;

import java.util.List;

public record VacationAccountSummaryDto(
		String employeeId,
		String employeeName,
		String username,
		String personalNumber,
		int year,
		int carryOverMinutes,
		int entitlementMinutes,
		int correctionsMinutes,
		int usageMinutes,
		int remainingMinutes,
		List<VacationAccountEntryDto> entries
) {
	public VacationAccountSummaryDto(String employeeId, int year, int carryOverMinutes, int entitlementMinutes,
			int correctionsMinutes, int usageMinutes, int remainingMinutes, List<VacationAccountEntryDto> entries) {
		this(employeeId, null, null, null, year, carryOverMinutes, entitlementMinutes, correctionsMinutes, usageMinutes,
				remainingMinutes, entries);
	}
}
