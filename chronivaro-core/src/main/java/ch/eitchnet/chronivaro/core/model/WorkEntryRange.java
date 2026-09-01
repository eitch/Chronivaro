package ch.eitchnet.chronivaro.core.model;

public record WorkEntryRange(String id, String start, String end, int durationMinutes,
							 String source, String createdBy, boolean modified, boolean isOnCall) {

	public WorkEntryRange(String id, String start, String end, int durationMinutes,
						  String source, String createdBy, boolean modified) {
		this(id, start, end, durationMinutes, source, createdBy, modified, false);
	}

	public WorkEntryRange(String id, String start, String end, int durationMinutes) {
		this(id, start, end, durationMinutes, null, null, false, false);
	}
}
