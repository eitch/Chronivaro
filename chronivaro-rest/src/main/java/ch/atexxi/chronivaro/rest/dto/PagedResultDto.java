package ch.atexxi.chronivaro.rest.dto;

import java.util.Collections;
import java.util.List;

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
}
