package ch.eitchnet.chronivaro.rest.dto;

public record WorkEntryRangeDto(String id, String start, String end, int durationMinutes,
								String source, String createdBy, boolean modified, boolean isOnCall) {

	public WorkEntryRangeDto(String id, String start, String end, int durationMinutes,
							String source, String createdBy, boolean modified) {
		this(id, start, end, durationMinutes, source, createdBy, modified, false);
	}

	public WorkEntryRangeDto(String id, String start, String end, int durationMinutes) {
		this(id, start, end, durationMinutes, null, null, false, false);
	}
}
