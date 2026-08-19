package ch.atexxi.chronivaro.rest.dto;

import java.util.List;

public record VacationAccountSummaryDto(
		String employeeId,
		int year,
		int carryOverMinutes,
		int entitlementMinutes,
		int correctionsMinutes,
		int usageMinutes,
		int remainingMinutes,
		List<VacationAccountEntryDto> entries
) {
}
