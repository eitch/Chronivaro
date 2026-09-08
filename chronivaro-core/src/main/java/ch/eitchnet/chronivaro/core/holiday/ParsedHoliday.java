package ch.eitchnet.chronivaro.core.holiday;

import java.time.LocalDate;

public record ParsedHoliday(LocalDate date, String name, double creditFactor) {

	public ParsedHoliday {
		if (date == null) {
			throw new IllegalArgumentException("date must not be null");
		}
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name must not be empty");
		}
		if (creditFactor <= 0.0) {
			creditFactor = 1.0;
		}
	}
}
