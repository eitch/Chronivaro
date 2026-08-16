package ch.atexxi.chronivaro.rest.dto;

import java.time.LocalDate;

public record EmployeeDto(String id, String personalNumber, String firstname, String lastname, LocalDate birthdate,
						  String teamId, String teamName, String locationId, String locationName, String timezone,
						  LocalDate joinDate, LocalDate exitDate, boolean active, String userId, String username,
						  String email, String scheduleTemplateId) {
}
