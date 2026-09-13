package ch.eitchnet.chronivaro.rest;

import ch.eitchnet.chronivaro.rest.dto.OnCallPeriodDto;
import ch.eitchnet.chronivaro.rest.resource.ChronivaroRestHelper;
import com.google.gson.reflect.TypeToken;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import org.junit.Test;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
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

		// 2. Query admin on-call periods with date range
		String periodId;
		try (Response response = target()
				.path("chronivaro/v1/admin/on-call-periods")
				.queryParam("employeeId", "admin_emp")
				.queryParam("from", "2026-09-01")
				.queryParam("to", "2026-09-30")
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

		// Verify out of range query returns empty
		try (Response response = target()
				.path("chronivaro/v1/admin/on-call-periods")
				.queryParam("employeeId", "admin_emp")
				.queryParam("from", "2026-10-01")
				.queryParam("to", "2026-10-31")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			String body = response.readEntity(String.class);
			Type listType = new TypeToken<List<OnCallPeriodDto>>() {}.getType();
			List<OnCallPeriodDto> list = ChronivaroRestHelper.createGson().fromJson(body, listType);
			assertTrue("Should return empty for out of range query", list.isEmpty());
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

		// 4b. Query my on-call periods endpoint
		try (Response response = target()
				.path("chronivaro/v1/me/on-call-periods")
				.queryParam("from", "2026-09-01")
				.queryParam("to", "2026-09-30")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			String body = response.readEntity(String.class);
			Type listType = new TypeToken<List<OnCallPeriodDto>>() {}.getType();
			List<OnCallPeriodDto> list = ChronivaroRestHelper.createGson().fromJson(body, listType);
			assertEquals(1, list.size());
		}

		// 4c. Query general on-call periods endpoint
		try (Response response = target()
				.path("chronivaro/v1/on-call-periods")
				.queryParam("from", "2026-09-01")
				.queryParam("to", "2026-09-30")
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

	@Test
	public void shouldFilterOnCallPeriodsByTeamForNormalEmployee() {
		String adminToken = authenticate("admin", "admin");
		String employeeToken = authenticate("employee", "admin");

		// Setup 2 teams and assign employees
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(runtimeMock.loginAdmin())) {
			tx.streamResources(TYPE_ON_CALL_PERIOD).forEach(tx::remove);
			Resource oldEmp2 = tx.getResourceBy(TYPE_EMPLOYEE, "emp_alpha_2");
			if (oldEmp2 != null) tx.remove(oldEmp2);
			Resource oldEmp3 = tx.getResourceBy(TYPE_EMPLOYEE, "emp_beta_1");
			if (oldEmp3 != null) tx.remove(oldEmp3);
			Resource oldEmpAlpha1 = tx.getResourceBy(TYPE_EMPLOYEE, "emp_alpha_1");
			if (oldEmpAlpha1 != null) tx.remove(oldEmpAlpha1);

			Resource loc = tx.getResourceBy(TYPE_LOCATION, "test-loc");
			if (loc == null) {
				loc = tx.getResourceTemplate(TYPE_LOCATION, true);
				loc.setId("test-loc");
				loc.setName("Test Location");
				loc.setString(PARAM_TIMEZONE, "Europe/Zurich");
				tx.add(loc);
			}

			Resource teamAlpha = tx.getResourceBy(TYPE_TEAM, "team-alpha");
			if (teamAlpha == null) {
				teamAlpha = tx.getResourceTemplate(TYPE_TEAM, true);
				teamAlpha.setId("team-alpha");
				teamAlpha.setName("Alpha Team");
				teamAlpha.setString(PARAM_NAME, "Alpha Team");
				tx.add(teamAlpha);
			}

			Resource teamBeta = tx.getResourceBy(TYPE_TEAM, "team-beta");
			if (teamBeta == null) {
				teamBeta = tx.getResourceTemplate(TYPE_TEAM, true);
				teamBeta.setId("team-beta");
				teamBeta.setName("Beta Team");
				teamBeta.setString(PARAM_NAME, "Beta Team");
				tx.add(teamBeta);
			}

			// Employee 1: user "employee" -> assign to teamAlpha
			Resource emp1 = tx.getResourceBy(TYPE_EMPLOYEE, "employee_emp");
			emp1.setRelation(PARAM_PRIMARY_TEAM, teamAlpha);
			tx.update(emp1);

			// Employee 2: in team Alpha
			Resource emp2 = tx.getResourceBy(TYPE_EMPLOYEE, "emp_alpha_2");
			if (emp2 == null) {
				emp2 = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
				emp2.setId("emp_alpha_2");
				emp2.setName("Alpha Employee 2");
				emp2.setString(PARAM_FIRSTNAME, "Alpha2");
				emp2.setString(PARAM_LASTNAME, "User");
				emp2.setString(PARAM_TIMEZONE, "Europe/Zurich");
				emp2.setDate(PARAM_JOIN_DATE, LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of("Europe/Zurich")));
				emp2.setBoolean(PARAM_ACTIVE, true);
				emp2.setRelation(PARAM_LOCATION, loc);
				emp2.setRelation(PARAM_PRIMARY_TEAM, teamAlpha);
				tx.add(emp2);
			} else {
				emp2.setRelation(PARAM_PRIMARY_TEAM, teamAlpha);
				tx.update(emp2);
			}

			// Employee 3: in team Beta
			Resource emp3 = tx.getResourceBy(TYPE_EMPLOYEE, "emp_beta_1");
			if (emp3 == null) {
				emp3 = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
				emp3.setId("emp_beta_1");
				emp3.setName("Beta Employee 1");
				emp3.setString(PARAM_FIRSTNAME, "Beta1");
				emp3.setString(PARAM_LASTNAME, "User");
				emp3.setString(PARAM_TIMEZONE, "Europe/Zurich");
				emp3.setDate(PARAM_JOIN_DATE, LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.of("Europe/Zurich")));
				emp3.setBoolean(PARAM_ACTIVE, true);
				emp3.setRelation(PARAM_LOCATION, loc);
				emp3.setRelation(PARAM_PRIMARY_TEAM, teamBeta);
				tx.add(emp3);
			} else {
				emp3.setRelation(PARAM_PRIMARY_TEAM, teamBeta);
				tx.update(emp3);
			}

			tx.commitOnClose();
		}

		// Create 3 on-call periods
		String createJson1 = """
				{
				  "employeeId": "employee_emp",
				  "startDate": "2026-11-01",
				  "startTime": "08:00",
				  "endDate": "2026-11-05",
				  "endTime": "17:00",
				  "comment": "Alpha 1 On-Call"
				}
				""";
		String createJson2 = """
				{
				  "employeeId": "emp_alpha_2",
				  "startDate": "2026-11-06",
				  "startTime": "08:00",
				  "endDate": "2026-11-10",
				  "endTime": "17:00",
				  "comment": "Alpha 2 On-Call"
				}
				""";
		String createJson3 = """
				{
				  "employeeId": "emp_beta_1",
				  "startDate": "2026-11-11",
				  "startTime": "08:00",
				  "endDate": "2026-11-15",
				  "endTime": "17:00",
				  "comment": "Beta 1 On-Call"
				}
				""";

		try (Response response = target()
				.path("chronivaro/v1/admin/on-call-periods")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.post(Entity.json(createJson1))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}
		try (Response response = target()
				.path("chronivaro/v1/admin/on-call-periods")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.post(Entity.json(createJson2))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}
		try (Response response = target()
				.path("chronivaro/v1/admin/on-call-periods")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.post(Entity.json(createJson3))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		// 1. Admin queries /on-call-periods for November -> sees all 3
		try (Response response = target()
				.path("chronivaro/v1/on-call-periods")
				.queryParam("from", "2026-11-01")
				.queryParam("to", "2026-11-30")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", adminToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			String body = response.readEntity(String.class);
			Type listType = new TypeToken<List<OnCallPeriodDto>>() {}.getType();
			List<OnCallPeriodDto> list = ChronivaroRestHelper.createGson().fromJson(body, listType);
			assertEquals(3, list.size());
		}

		// 2. Normal employee in Team Alpha queries /on-call-periods -> sees only Team Alpha (2 periods)
		try (Response response = target()
				.path("chronivaro/v1/on-call-periods")
				.queryParam("from", "2026-11-01")
				.queryParam("to", "2026-11-30")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			String body = response.readEntity(String.class);
			Type listType = new TypeToken<List<OnCallPeriodDto>>() {}.getType();
			List<OnCallPeriodDto> list = ChronivaroRestHelper.createGson().fromJson(body, listType);
			assertEquals(2, list.size());
			assertTrue(list.stream().anyMatch(p -> p.employeeId().equals("employee_emp")));
			assertTrue(list.stream().anyMatch(p -> p.employeeId().equals("emp_alpha_2")));
			assertFalse(list.stream().anyMatch(p -> p.employeeId().equals("emp_beta_1")));
		}

		// 3. Normal employee queries for emp_alpha_2 specifically -> succeeds
		try (Response response = target()
				.path("chronivaro/v1/on-call-periods")
				.queryParam("employeeId", "emp_alpha_2")
				.queryParam("from", "2026-11-01")
				.queryParam("to", "2026-11-30")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			String body = response.readEntity(String.class);
			Type listType = new TypeToken<List<OnCallPeriodDto>>() {}.getType();
			List<OnCallPeriodDto> list = ChronivaroRestHelper.createGson().fromJson(body, listType);
			assertEquals(1, list.size());
			assertEquals("emp_alpha_2", list.get(0).employeeId());
		}

		// 4. Normal employee queries for emp_beta_1 (different team) -> returns empty list
		try (Response response = target()
				.path("chronivaro/v1/on-call-periods")
				.queryParam("employeeId", "emp_beta_1")
				.queryParam("from", "2026-11-01")
				.queryParam("to", "2026-11-30")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			String body = response.readEntity(String.class);
			Type listType = new TypeToken<List<OnCallPeriodDto>>() {}.getType();
			List<OnCallPeriodDto> list = ChronivaroRestHelper.createGson().fromJson(body, listType);
			assertTrue(list.isEmpty());
		}
	}
}
