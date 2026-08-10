package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.rest.dto.EmployeeDto;
import ch.atexxi.chronivaro.rest.resource.ChronivaroRestHelper;
import com.google.gson.reflect.TypeToken;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class EmployeeResourceTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldPerformCrudOnEmployees() {
		String authToken = authenticate();

		// Create
		EmployeeDto newEmployee = new EmployeeDto(null, "PN001", "Test Employee", "team-1", "location-1",
				"Europe/Zurich", LocalDate.of(2025, 1, 1), null, true, "admin");
		String json = ChronivaroRestHelper.createGson().toJson(newEmployee);

		try (Response response = target()
				.path("chronivaro/v1/admin/employees")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(json))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		String employeeId;
		// Get all
		try (Response response = target()
				.path("chronivaro/v1/admin/employees")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			List<EmployeeDto> employees = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<EmployeeDto>>() {
					}.getType());
			employeeId = employees
					.stream()
					.filter(e -> e.displayName().equals("Test Employee"))
					.findFirst()
					.orElseThrow()
					.id();
		}

		// Get by ID
		try (Response response = target()
				.path("chronivaro/v1/admin/employees/" + employeeId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			EmployeeDto employee = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), EmployeeDto.class);
			assertEquals("Test Employee", employee.displayName());
		}

		// Update
		EmployeeDto updatedEmployee = new EmployeeDto(employeeId, "PN001", "Updated Test Employee", "team-1",
				"location-1", "Europe/Zurich", LocalDate.of(2025, 1, 1), null, false, "admin");
		String updatedJson = ChronivaroRestHelper.createGson().toJson(updatedEmployee);

		try (Response response = target()
				.path("chronivaro/v1/admin/employees/" + employeeId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.put(Entity.json(updatedJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Verify update
		try (Response response = target()
				.path("chronivaro/v1/admin/employees/" + employeeId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			EmployeeDto employee = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), EmployeeDto.class);
			assertEquals("Updated Test Employee", employee.displayName());
			assertFalse(employee.active());
		}

		// Delete
		try (Response response = target()
				.path("chronivaro/v1/admin/employees/" + employeeId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.delete()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// Verify deletion
		try (Response response = target()
				.path("chronivaro/v1/admin/employees/" + employeeId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
		}
	}

	@Test
	public void shouldCreateSchedule() {
		String authToken = authenticate();

		String json = """
				{
				  "validFrom": "2025-01-01T00:00:00+01:00",
				  "monday": 480,
				  "tuesday": 480,
				  "wednesday": 480,
				  "thursday": 480,
				  "friday": 480
				}
				""";
		try (Response response = target()
				.path("chronivaro/v1/admin/employees/admin_emp/schedules")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(json))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}
	}

	@Test
	public void shouldAddVacationCorrection() {
		String authToken = authenticate();

		String json = """
				{
				  "value": 480,
				  "comment": "Bonus vacation"
				}
				""";
		try (Response response = target()
				.path("chronivaro/v1/admin/employees/admin_emp/vacation-corrections")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(json))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}
	}
}
