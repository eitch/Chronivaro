package ch.eitchnet.chronivaro.rest.dto;

import java.time.ZonedDateTime;

public record PersonalAccessTokenCreatedDto(
		String tokenId,
		String username,
		String name,
		String preset,
		ZonedDateTime validFrom,
		ZonedDateTime validTo,
		String token) {
}
