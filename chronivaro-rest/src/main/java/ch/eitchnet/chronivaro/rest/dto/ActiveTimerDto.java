package ch.eitchnet.chronivaro.rest.dto;

import ch.eitchnet.chronivaro.core.model.WorkingLocation;

import java.time.ZonedDateTime;

public record ActiveTimerDto(String id, ZonedDateTime start, WorkingLocation workingLocation, boolean isPreviousDay) {
}
