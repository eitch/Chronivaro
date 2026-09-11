package ch.eitchnet.chronivaro.rest.dto;

import java.time.LocalDate;

public record EmployeeDto(String id, String personalNumber, String firstname, String lastname, LocalDate birthdate,
						  String teamId, String teamName, String locationId, String locationName, String timezone,
						  LocalDate joinDate, LocalDate exitDate, boolean active, String userId, String username,
						  String email, String scheduleTemplateId,
						  LocalDate scheduleValidFrom, Integer initialOvertimeMinutes, Double initialVacationDays) {

	public EmployeeDto(String id, String personalNumber, String firstname, String lastname, LocalDate birthdate,
					   String teamId, String teamName, String locationId, String locationName, String timezone,
					   LocalDate joinDate, LocalDate exitDate, boolean active, String userId, String username,
					   String email, String scheduleTemplateId) {
		this(id, personalNumber, firstname, lastname, birthdate, teamId, teamName, locationId, locationName, timezone,
				joinDate, exitDate, active, userId, username, email, scheduleTemplateId, null, null, null);
	}
}
