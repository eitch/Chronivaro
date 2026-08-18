package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.WorkingLocation;
import ch.atexxi.chronivaro.rest.dto.EmployeeDto;
import ch.atexxi.chronivaro.rest.dto.ErrorDto;
import ch.atexxi.chronivaro.rest.dto.TeamDto;
import ch.atexxi.chronivaro.rest.dto.WorkEntryDto;
import ch.atexxi.chronivaro.rest.resource.ChronivaroRestHelper;
import ch.atexxi.chronivaro.rest.resource.ConcurrencyHelper;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.TYPE_TEAM;
import static org.junit.Assert.*;

public class RestConcurrencyTest extends AbstractChronivaroRestfulTest {

	@Test
	public void testConcurrencyHelperParsers() {
		assertEquals(Integer.valueOf(0), ConcurrencyHelper.parseIfMatchHeader("0"));
		assertEquals(Integer.valueOf(1), ConcurrencyHelper.parseIfMatchHeader("\"1\""));
		assertEquals(Integer.valueOf(2), ConcurrencyHelper.parseIfMatchHeader("W/\"2\""));
		assertEquals(Integer.valueOf(3), ConcurrencyHelper.parseIfMatchHeader("w/\"3\""));
		assertNull(ConcurrencyHelper.parseIfMatchHeader(null));
		assertNull(ConcurrencyHelper.parseIfMatchHeader(""));
		assertNull(ConcurrencyHelper.parseIfMatchHeader("   "));
		assertNull(ConcurrencyHelper.parseIfMatchHeader("*"));
	}

	@Test
	public void shouldEnforceOptimisticConcurrencyOnTeams() {
		String authToken = authenticate();

		// 1. Create a team
		TeamDto newTeam = new TeamDto(null, "Concurrency Test Team");
		try (Response response = target()
				.path("chronivaro/v1/admin/teams")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(ChronivaroRestHelper.createGson().toJson(newTeam)))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
		}

		String teamId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(runtimeMock.loginAdmin())) {
			Resource team = tx
					.streamResources(TYPE_TEAM)
					.filter(r -> "Concurrency Test Team".equals(r.getName()))
					.findFirst()
					.orElseThrow();
			teamId = team.getId();
		}

		// 2. GET team and verify ETag header
		String etag;
		try (Response response = target()
				.path("chronivaro/v1/admin/teams/" + teamId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			etag = response.getHeaderString(HttpHeaders.ETAG);
			assertNotNull("ETag must be returned on GET", etag);
			assertEquals("\"0\"", etag);
		}

		// 3. Stale update with wrong/old If-Match (e.g. If-Match: "99") must return 409 Conflict
		TeamDto updateDto1 = new TeamDto(teamId, "Conflict Team Name");
		try (Response response = target()
				.path("chronivaro/v1/admin/teams/" + teamId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.header(HttpHeaders.IF_MATCH, "\"99\"")
				.put(Entity.json(ChronivaroRestHelper.createGson().toJson(updateDto1)))) {
			assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
			ErrorDto error = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), ErrorDto.class);
			assertEquals("CONCURRENCY_CONFLICT", error.errorCode());
			assertNotNull(error.correlationId());
		}

		// 4. Update with matching If-Match: "0" succeeds and returns new ETag: "1"
		TeamDto updateDto2 = new TeamDto(teamId, "Team Concurrency Updated 1");
		try (Response response = target()
				.path("chronivaro/v1/admin/teams/" + teamId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.header(HttpHeaders.IF_MATCH, "\"0\"")
				.put(Entity.json(ChronivaroRestHelper.createGson().toJson(updateDto2)))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			String newEtag = response.getHeaderString(HttpHeaders.ETAG);
			assertNotNull("New ETag must be returned on successful update", newEtag);
			assertEquals("\"1\"", newEtag);
		}

		// 5. Subsequent update using previous If-Match: "0" must now fail with 409 Conflict
		try (Response response = target()
				.path("chronivaro/v1/admin/teams/" + teamId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.header(HttpHeaders.IF_MATCH, "\"0\"")
				.put(Entity.json(ChronivaroRestHelper.createGson().toJson(updateDto2)))) {
			assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
			ErrorDto error = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), ErrorDto.class);
			assertEquals("CONCURRENCY_CONFLICT", error.errorCode());
		}

		// 6. Update with weak ETag format W/"1" succeeds and returns ETag: "2"
		TeamDto updateDto3 = new TeamDto(teamId, "Team Concurrency Updated 2");
		try (Response response = target()
				.path("chronivaro/v1/admin/teams/" + teamId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.header(HttpHeaders.IF_MATCH, "W/\"1\"")
				.put(Entity.json(ChronivaroRestHelper.createGson().toJson(updateDto3)))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			assertEquals("\"2\"", response.getHeaderString(HttpHeaders.ETAG));
		}

		// 7. Invalid If-Match header value returns 400 Bad Request
		try (Response response = target()
				.path("chronivaro/v1/admin/teams/" + teamId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.header(HttpHeaders.IF_MATCH, "invalid-version")
				.put(Entity.json(ChronivaroRestHelper.createGson().toJson(updateDto3)))) {
			assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
			ErrorDto error = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), ErrorDto.class);
			assertEquals("INVALID_IF_MATCH_HEADER", error.errorCode());
		}
	}

	@Test
	public void shouldEnforceOptimisticConcurrencyOnEmployee() {
		String authToken = authenticate();

		// Create employee
		EmployeeDto createDto = new EmployeeDto(null, "E-CONCURR", "Alice", "Concurrency",
				LocalDate.of(1990, 1, 1), "team-1", "Development", "location-1", "Zurich",
				"Europe/Zurich", LocalDate.of(2025, 1, 1), null, true, null,
				"aconcurrency", "aconcurrency@atexxi.ch", null);

		String employeeId;
		try (Response response = target()
				.path("chronivaro/v1/admin/employees")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.post(Entity.json(ChronivaroRestHelper.createGson().toJson(createDto)))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			EmployeeDto created = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), EmployeeDto.class);
			employeeId = created.id();
		}

		// GET employee
		try (Response response = target()
				.path("chronivaro/v1/admin/employees/" + employeeId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			assertEquals("\"0\"", response.getHeaderString(HttpHeaders.ETAG));
		}

		// Stale update
		EmployeeDto updateDto = new EmployeeDto(employeeId, "E-CONCURR", "Alice", "Concurrency-Updated",
				LocalDate.of(1990, 1, 1), "team-1", "Development", "location-1", "Zurich",
				"Europe/Zurich", LocalDate.of(2025, 1, 1), null, true, null,
				"aconcurrency", "aconcurrency@atexxi.ch", null);

		try (Response response = target()
				.path("chronivaro/v1/admin/employees/" + employeeId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.header(HttpHeaders.IF_MATCH, "\"5\"")
				.put(Entity.json(ChronivaroRestHelper.createGson().toJson(updateDto)))) {
			assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
		}

		// Valid update with If-Match "0"
		try (Response response = target()
				.path("chronivaro/v1/admin/employees/" + employeeId)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.header(HttpHeaders.IF_MATCH, "\"0\"")
				.put(Entity.json(ChronivaroRestHelper.createGson().toJson(updateDto)))) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			assertEquals("\"1\"", response.getHeaderString(HttpHeaders.ETAG));
		}
	}

	@Test
	public void shouldEnforceOptimisticConcurrencyOnLocationAndAbsenceType() {
		String authToken = authenticate();

		// Location concurrency
		try (Response response = target()
				.path("chronivaro/v1/admin/locations/location-1")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			assertNotNull(response.getHeaderString(HttpHeaders.ETAG));
		}

		// AbsenceType concurrency
		try (Response response = target()
				.path("chronivaro/v1/admin/absence-types/vacation")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			assertNotNull(response.getHeaderString(HttpHeaders.ETAG));
		}
	}
}
