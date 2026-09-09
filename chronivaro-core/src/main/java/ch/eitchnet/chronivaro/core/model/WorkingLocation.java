package ch.eitchnet.chronivaro.core.model;

public enum WorkingLocation {
	OFFICE,
	HOME_OFFICE,
	FIELD,
	REMOTE;

	public static WorkingLocation fromValue(String value) {
		return value == null || value.isEmpty() ? null : valueOf(value);
	}
}