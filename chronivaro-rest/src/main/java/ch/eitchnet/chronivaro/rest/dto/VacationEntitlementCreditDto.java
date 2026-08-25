package ch.eitchnet.chronivaro.rest.dto;

public record VacationEntitlementCreditDto(
		String employeeId,
		int year,
		int entitlementMinutes,
		String entryId
) {
}
