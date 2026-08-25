package ch.eitchnet.chronivaro.rest.dto;

import java.time.LocalDate;

public record HolidayDto(String id, String holidayCalendarId, LocalDate date, String name, double creditFactor) {
}
