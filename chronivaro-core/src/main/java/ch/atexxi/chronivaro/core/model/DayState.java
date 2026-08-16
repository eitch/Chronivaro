package ch.atexxi.chronivaro.core.model;

public enum DayState {
	NOT_WORKING("Not working"),
	WORKING("Working");

	private final String label;

	DayState(String label) {
		this.label = label;
	}

	public String getLabel() {
		return this.label;
	}
}
