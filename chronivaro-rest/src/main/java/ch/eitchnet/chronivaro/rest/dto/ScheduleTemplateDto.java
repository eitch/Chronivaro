package ch.eitchnet.chronivaro.rest.dto;

public record ScheduleTemplateDto(String id, String name, int monday, int tuesday, int wednesday, int thursday,
								  int friday, int saturday, int sunday) {
}
