package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.rest.dto.*;
import ch.atexxi.chronivaro.rest.resource.ChronivaroRestHelper;
import ch.atexxi.chronivaro.rest.resource.PaginationHelper;
import ch.atexxi.chronivaro.rest.resource.RestException;
import com.google.gson.reflect.TypeToken;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.search.SearchResult;
import org.junit.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

public class RestPaginationTest extends AbstractChronivaroRestfulTest {

	@Test
	public void testPaginationValidationHelper() {
		// Valid
		PaginationHelper.validate(null, null);
		PaginationHelper.validate(0, 50);
		PaginationHelper.validate(10, 1000);

		// Invalid offset
		try {
			PaginationHelper.validate(-1, 50);
			fail("Expected RestException for negative offset");
		} catch (RestException ex) {
			assertEquals(Response.Status.BAD_REQUEST, ex.getStatus());
			assertEquals("INVALID_PAGINATION", ex.getErrorCode());
			assertEquals(1, ex.getFieldErrors().size());
			assertEquals("offset", ex.getFieldErrors().getFirst().field());
		}

		// Invalid limit <= 0
		try {
			PaginationHelper.validate(0, 0);
			fail("Expected RestException for limit 0");
		} catch (RestException ex) {
			assertEquals(Response.Status.BAD_REQUEST, ex.getStatus());
			assertEquals("INVALID_PAGINATION", ex.getErrorCode());
			assertEquals(1, ex.getFieldErrors().size());
			assertEquals("limit", ex.getFieldErrors().getFirst().field());
		}

		// Invalid limit > 1000
		try {
			PaginationHelper.validate(0, 1001);
			fail("Expected RestException for limit > 1000");
		} catch (RestException ex) {
			assertEquals(Response.Status.BAD_REQUEST, ex.getStatus());
			assertEquals("INVALID_PAGINATION", ex.getErrorCode());
			assertEquals(1, ex.getFieldErrors().size());
			assertEquals("limit", ex.getFieldErrors().getFirst().field());
		}
	}

	@Test
	public void testListPaginationHelper() {
		List<String> items = IntStream.range(0, 10).mapToObj(i -> "Item-" + i).toList();

		PagedResultDto<String> page1 = PaginationHelper.toPagedResult(items, 0, 3);
		assertEquals(3, page1.data().size());
		assertEquals(3, page1.size());
		assertEquals(0, page1.offset());
		assertEquals(3, page1.limit());
		assertEquals(10, page1.total());
		assertEquals("Item-0", page1.data().getFirst());
		assertEquals("Item-2", page1.data().getLast());

		PagedResultDto<String> page4 = PaginationHelper.toPagedResult(items, 9, 3);
		assertEquals(1, page4.data().size());
		assertEquals(1, page4.size());
		assertEquals("Item-9", page4.data().getFirst());

		PagedResultDto<String> pageOutOfBounds = PaginationHelper.toPagedResult(items, 15, 3);
		assertEquals(0, pageOutOfBounds.data().size());
		assertEquals(0, pageOutOfBounds.size());
		assertEquals(10, pageOutOfBounds.total());
	}

	@Test
	public void testSearchResultPaginationHelper() {
		List<String> items = IntStream.range(0, 10).mapToObj(i -> "Entry-" + i).toList();
		SearchResult<String> searchResult = new SearchResult<>(items.stream());

		PagedResultDto<String> paged = PaginationHelper.toPagedResult(searchResult, 0, 4, String::toUpperCase);
		assertEquals(4, paged.data().size());
		assertEquals(4, paged.size());
		assertEquals(0, paged.offset());
		assertEquals(4, paged.limit());
		assertEquals(10, paged.total());
		assertEquals("ENTRY-0", paged.data().getFirst());
	}

	@Test
	public void shouldReturnPagedEmployees() {
		String authToken = authenticate();

		try (Response response = target()
				.path("chronivaro/v1/admin/employees")
				.queryParam("offset", 0)
				.queryParam("limit", 1)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			String json = response.readEntity(String.class);

			PagedResultDto<EmployeeDto> pagedResult = ChronivaroRestHelper
					.createGson()
					.fromJson(json, new TypeToken<PagedResultDto<EmployeeDto>>() {
					}.getType());

			assertNotNull(pagedResult);
			assertEquals(0, pagedResult.offset());
			assertEquals(1, pagedResult.limit());
			assertTrue(pagedResult.total() >= 1);
			assertEquals(1, pagedResult.size());
			assertEquals(1, pagedResult.data().size());
		}
	}

	@Test
	public void shouldRejectInvalidPaginationParameters() {
		String authToken = authenticate();

		// Negative offset
		try (Response response = target()
				.path("chronivaro/v1/admin/employees")
				.queryParam("offset", -1)
				.queryParam("limit", 10)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
			ErrorDto error = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), ErrorDto.class);
			assertEquals("INVALID_PAGINATION", error.errorCode());
			assertEquals(1, error.fieldErrors().size());
			assertEquals("offset", error.fieldErrors().getFirst().field());
		}

		// Limit = 0
		try (Response response = target()
				.path("chronivaro/v1/admin/employees")
				.queryParam("offset", 0)
				.queryParam("limit", 0)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
			ErrorDto error = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), ErrorDto.class);
			assertEquals("INVALID_PAGINATION", error.errorCode());
			assertEquals(1, error.fieldErrors().size());
			assertEquals("limit", error.fieldErrors().getFirst().field());
		}

		// Limit > 1000
		try (Response response = target()
				.path("chronivaro/v1/admin/employees")
				.queryParam("offset", 0)
				.queryParam("limit", 1001)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
			ErrorDto error = ChronivaroRestHelper.createGson().fromJson(response.readEntity(String.class), ErrorDto.class);
			assertEquals("INVALID_PAGINATION", error.errorCode());
			assertEquals(1, error.fieldErrors().size());
			assertEquals("limit", error.fieldErrors().getFirst().field());
		}
	}

	@Test
	public void shouldReturnPagedTeams() {
		String authToken = authenticate();

		try (Response response = target()
				.path("chronivaro/v1/admin/teams")
				.queryParam("offset", 0)
				.queryParam("limit", 10)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			PagedResultDto<TeamDto> pagedResult = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<PagedResultDto<TeamDto>>() {
					}.getType());

			assertNotNull(pagedResult);
			assertEquals(0, pagedResult.offset());
			assertEquals(10, pagedResult.limit());
			assertTrue(pagedResult.total() >= 1);
		}
	}

	@Test
	public void shouldReturnPagedPresence() {
		String authToken = authenticate();

		try (Response response = target()
				.path("chronivaro/v1/presence")
				.queryParam("offset", 0)
				.queryParam("limit", 5)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", authToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			PagedResultDto<PresenceDto> pagedResult = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<PagedResultDto<PresenceDto>>() {
					}.getType());

			assertNotNull(pagedResult);
			assertEquals(0, pagedResult.offset());
			assertEquals(5, pagedResult.limit());
		}
	}
}
