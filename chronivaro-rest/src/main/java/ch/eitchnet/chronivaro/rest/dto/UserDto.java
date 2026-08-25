package ch.eitchnet.chronivaro.rest.dto;

import java.util.Set;

public record UserDto(String id, String username, String firstname, String lastname, String email,
					  Set<String> roles, String state, String locale,
					  boolean hasLinkedEmployee, String employeeId, String employeeName) {
}
