package ch.atexxi.chronivaro.rest.dto;

public record WorkEntryRangeDto(String id, String start, String end, int durationMinutes,
								String source, String createdBy, boolean modified) {

	public WorkEntryRangeDto(String id, String start, String end, int durationMinutes) {
		this(id, start, end, durationMinutes, null, null, false);
	}
}
