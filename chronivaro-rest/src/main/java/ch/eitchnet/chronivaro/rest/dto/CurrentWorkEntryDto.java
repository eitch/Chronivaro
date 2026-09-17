package ch.eitchnet.chronivaro.rest.dto;

import java.time.ZonedDateTime;

public record CurrentWorkEntryDto(
		String id,
		ZonedDateTime start,
		String workingLocation,
		String comment
) {
}
