package ch.eitchnet.chronivaro.rest;

import ch.eitchnet.chronivaro.rest.dto.OnCallPeriodDto;
import ch.eitchnet.chronivaro.rest.resource.ChronivaroRestHelper;
import com.google.gson.reflect.TypeToken;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.*;

public class OnCallPeriodResourceTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldManageOnCallPeriodsViaRest() {
		String adminToken = authenticate();

		// 1. Create on-call period
		String createJson = """
				{
				  "employeeId": "admin_emp",
				  "startDate": "2026-09-01",
				  "startTime": "08:00",
				  "endDate": "2026-09-07",
				  "endTime": "17:00",
				  "comment": "Weekend & Week Pikett"
				}
				""";

		try (Response response = target()
				.path("chronivaro/v1/admin/on-call-periods")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.post(Entity.json(createJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// 2. Query admin on-call periods
		String periodId;
		try (Response response = target()
				.path("chronivaro/v1/admin/on-call-periods")
				.queryParam("employeeId", "admin_emp")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			String body = response.readEntity(String.class);
			Type listType = new TypeToken<List<OnCallPeriodDto>>() {}.getType();
			List<OnCallPeriodDto> list = ChronivaroRestHelper.createGson().fromJson(body, listType);
			assertFalse("Must find created period", list.isEmpty());
			OnCallPeriodDto dto = list.get(0);
			periodId = dto.id();
			assertEquals("admin_emp", dto.employeeId());
			assertEquals(LocalDate.of(2026, 9, 1), dto.startDate());
			assertEquals("08:00", dto.startTime());
			assertEquals("Weekend & Week Pikett", dto.comment());
		}

		// 3. Update on-call period
		String updateJson = """
				{
				  "startDate": "2026-09-01",
				  "startTime": "09:00",
				  "endDate": "2026-09-08",
				  "endTime": "18:00",
				  "comment": "Updated Pikett"
				}
				""";

		try (Response response = target()
				.path("chronivaro/v1/admin/on-call-periods/" + periodId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.put(Entity.json(updateJson))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			String body = response.readEntity(String.class);
			OnCallPeriodDto updated = ChronivaroRestHelper.createGson().fromJson(body, OnCallPeriodDto.class);
			assertEquals("Updated Pikett", updated.comment());
			assertEquals("09:00", updated.startTime());
		}

		// 4. Query employee on-call periods endpoint
		try (Response response = target()
				.path("chronivaro/v1/employees/admin_emp/on-call-periods")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			String body = response.readEntity(String.class);
			Type listType = new TypeToken<List<OnCallPeriodDto>>() {}.getType();
			List<OnCallPeriodDto> list = ChronivaroRestHelper.createGson().fromJson(body, listType);
			assertEquals(1, list.size());
		}

		// 5. Delete on-call period
		try (Response response = target()
				.path("chronivaro/v1/admin/on-call-periods/" + periodId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.delete()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// 6. Verify deletion
		try (Response response = target()
				.path("chronivaro/v1/admin/on-call-periods")
				.queryParam("employeeId", "admin_emp")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			String body = response.readEntity(String.class);
			Type listType = new TypeToken<List<OnCallPeriodDto>>() {}.getType();
			List<OnCallPeriodDto> list = ChronivaroRestHelper.createGson().fromJson(body, listType);
			assertTrue(list.isEmpty());
		}
	}
}
