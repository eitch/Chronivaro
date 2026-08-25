package ch.eitchnet.chronivaro.rest.dto;

public record VacationEntitlementCalculationDto(
		String employeeId,
		int year,
		int entitlementMinutes,
		VacationAccountSummaryDto summary
) {
}
