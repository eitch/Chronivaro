package ch.atexxi.chronivaro.rest.dto;

import li.strolch.utils.collections.Paging;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public record PagedResultDto<T>(List<T> data, int offset, int limit, long total, int size) {

	public PagedResultDto(List<T> data, int offset, int limit, long total) {
		this(data == null ? Collections.emptyList() : data, offset, limit, total, data == null ? 0 : data.size());
	}

	public static <T> PagedResultDto<T> empty(int offset, int limit) {
		return new PagedResultDto<>(Collections.emptyList(), offset, limit, 0, 0);
	}

	public static <T> PagedResultDto<T> of(List<T> data, int offset, int limit, long total) {
		return new PagedResultDto<>(data, offset, limit, total);
	}

	public static <T> PagedResultDto<T> from(Paging<T> paging) {
		if (paging == null)
			return empty(0, 0);
		return new PagedResultDto<>(paging.getPage(), paging.getOffset(), paging.getLimit(), paging.getSize());
	}

	public static <T, R> PagedResultDto<R> from(Paging<T> paging, Function<T, R> mapper) {
		if (paging == null)
			return empty(0, 0);
		List<R> mappedData = paging.getPage().stream().map(mapper).toList();
		return new PagedResultDto<>(mappedData, paging.getOffset(), paging.getLimit(), paging.getSize());
	}
}
