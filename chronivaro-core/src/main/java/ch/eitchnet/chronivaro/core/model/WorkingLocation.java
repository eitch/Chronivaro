package ch.eitchnet.chronivaro.core.model;

public enum WorkingLocation {
	HOME_OFFICE,
	OFFICE,
	CUSTOMER;

	public static WorkingLocation fromValue(String value) {
		return value == null || value.isEmpty() ? null : valueOf(value);
	}
}