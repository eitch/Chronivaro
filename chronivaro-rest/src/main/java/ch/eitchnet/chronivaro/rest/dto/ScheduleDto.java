package ch.eitchnet.chronivaro.rest.dto;

import java.time.ZonedDateTime;

public record ScheduleDto(String id, String employeeId, ZonedDateTime validFrom, ZonedDateTime validTo, int monday,
						  int tuesday, int wednesday, int thursday, int friday, int saturday, int sunday) {
}
