package ch.eitchnet.chronivaro.core.model;

public record VacationAccountSummary(
		String employeeId,
		int year,
		int carryOverMinutes,
		int entitlementMinutes,
		int correctionsMinutes,
		int usageMinutes,
		int remainingMinutes
) {
}
