package ch.atexxi.chronivaro.core.model;

public record WorkEntryRange(String id, String start, String end, int durationMinutes,
							 String source, String createdBy, boolean modified) {

	public WorkEntryRange(String id, String start, String end, int durationMinutes) {
		this(id, start, end, durationMinutes, null, null, false);
	}
}
