package ch.eitchnet.chronivaro.rest.dto;

import java.time.ZonedDateTime;

public record CreatePersonalAccessTokenRequestDto(
		String name,
		String preset,
		ZonedDateTime validTo) {
}
