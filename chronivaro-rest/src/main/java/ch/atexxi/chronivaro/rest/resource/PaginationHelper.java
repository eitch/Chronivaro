package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.rest.dto.FieldErrorDto;
import ch.atexxi.chronivaro.rest.dto.PagedResultDto;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.search.SearchResult;
import li.strolch.utils.collections.Paging;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class PaginationHelper {

	public static final int DEFAULT_OFFSET = 0;
	public static final int DEFAULT_LIMIT = 50;
	public static final int MAX_LIMIT = 1000;

	public static void validate(Integer offset, Integer limit) {
		if (offset != null && offset < 0) {
			throw new RestException(Response.Status.BAD_REQUEST, "INVALID_PAGINATION", "Offset must be non-negative",
					List.of(new FieldErrorDto("offset", "MIN_VALUE")));
		}
		if (limit != null && (limit <= 0 || limit > MAX_LIMIT)) {
			throw new RestException(Response.Status.BAD_REQUEST, "INVALID_PAGINATION",
					"Limit must be between 1 and " + MAX_LIMIT, List.of(new FieldErrorDto("limit", "INVALID_RANGE")));
		}
	}

	public static int sanitizeOffset(Integer offset) {
		return offset == null ? DEFAULT_OFFSET : offset;
	}

	public static int sanitizeLimit(Integer limit) {
		return limit == null ? DEFAULT_LIMIT : limit;
	}

	public static boolean isPaginationRequested(Integer offset, Integer limit) {
		return offset != null || limit != null;
	}

	public static <T, R> PagedResultDto<R> toPagedResult(SearchResult<T> searchResult, Integer offsetParam,
			Integer limitParam, Function<T, R> mapper) {
		validate(offsetParam, limitParam);
		int offset = sanitizeOffset(offsetParam);
		int limit = sanitizeLimit(limitParam);

		if (searchResult == null)
			return PagedResultDto.empty(offset, limit);

		Paging<T> paging = searchResult.toPaging(offset, limit);
		long total = paging.getSize();
		List<R> data = paging.getPage().stream().map(mapper).toList();
		return PagedResultDto.of(data, offset, limit, total);
	}

	public static <T> PagedResultDto<T> toPagedResult(SearchResult<T> searchResult, Integer offsetParam,
			Integer limitParam) {
		return toPagedResult(searchResult, offsetParam, limitParam, Function.identity());
	}

	public static <T, R> PagedResultDto<R> toPagedResult(List<T> allItems, Integer offsetParam, Integer limitParam,
			Function<T, R> mapper) {
		validate(offsetParam, limitParam);
		int offset = sanitizeOffset(offsetParam);
		int limit = sanitizeLimit(limitParam);

		if (allItems == null || allItems.isEmpty())
			return PagedResultDto.empty(offset, limit);

		int total = allItems.size();
		int fromIndex = Math.min(offset, total);
		int toIndex = Math.min(fromIndex + limit, total);
		List<R> data = allItems.subList(fromIndex, toIndex).stream().map(mapper).toList();
		return PagedResultDto.of(data, offset, limit, total);
	}

	public static <T> PagedResultDto<T> toPagedResult(List<T> allItems, Integer offsetParam, Integer limitParam) {
		return toPagedResult(allItems, offsetParam, limitParam, Function.identity());
	}

	public static <T, R> Response toPagedOrListResponse(SearchResult<T> searchResult, Integer offset, Integer limit,
			Function<T, R> mapper) {
		if (isPaginationRequested(offset, limit)) {
			PagedResultDto<R> pagedResult = toPagedResult(searchResult, offset, limit, mapper);
			return toPagedResponse(pagedResult);
		}
		List<R> list = searchResult.toList().stream().map(mapper).toList();
		return Response.ok(ChronivaroRestHelper.createGson().toJson(list), MediaType.APPLICATION_JSON).build();
	}

	public static <T, R> Response toPagedOrListResponse(List<T> allItems, Integer offset, Integer limit,
			Function<T, R> mapper) {
		if (isPaginationRequested(offset, limit)) {
			PagedResultDto<R> pagedResult = toPagedResult(allItems, offset, limit, mapper);
			return toPagedResponse(pagedResult);
		}
		List<R> list = (allItems == null ? Collections.<T>emptyList() : allItems).stream().map(mapper).toList();
		return Response.ok(ChronivaroRestHelper.createGson().toJson(list), MediaType.APPLICATION_JSON).build();
	}

	public static Response toPagedResponse(PagedResultDto<?> pagedResult) {
		return Response.ok(ChronivaroRestHelper.createGson().toJson(pagedResult), MediaType.APPLICATION_JSON).build();
	}
}
