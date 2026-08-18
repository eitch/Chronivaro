package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.StrolchRootElement;

public final class ConcurrencyHelper {

	private ConcurrencyHelper() {
	}

	public static int getVersion(StrolchRootElement element) {
		return ChronivaroVersionHelper.getVersion(element);
	}

	public static EntityTag toEntityTag(StrolchRootElement element) {
		return new EntityTag(String.valueOf(getVersion(element)));
	}

	public static String toETagHeader(StrolchRootElement element) {
		return "\"" + getVersion(element) + "\"";
	}

	public static Integer parseIfMatchHeader(String ifMatch) {
		if (ifMatch == null || ifMatch.isBlank() || ifMatch.trim().equals("*"))
			return null;

		String trimmed = ifMatch.trim();
		if (trimmed.startsWith("W/") || trimmed.startsWith("w/"))
			trimmed = trimmed.substring(2).trim();
		if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2)
			trimmed = trimmed.substring(1, trimmed.length() - 1).trim();

		try {
			return Integer.parseInt(trimmed);
		} catch (NumberFormatException e) {
			throw new RestException(Response.Status.BAD_REQUEST, "INVALID_IF_MATCH_HEADER",
					"Invalid If-Match header value: " + ifMatch);
		}
	}

	public static void validateIfMatch(String ifMatch, StrolchRootElement element) {
		if (ifMatch == null || ifMatch.isBlank() || ifMatch.trim().equals("*") || element == null)
			return;

		Integer expectedVersion = parseIfMatchHeader(ifMatch);
		if (expectedVersion == null)
			return;

		int currentVersion = getVersion(element);
		if (expectedVersion != currentVersion) {
			throw new RestException(Response.Status.CONFLICT, "CONCURRENCY_CONFLICT",
					"Resource '" + element.getId() + "' of type '" + element.getType()
							+ "' was modified concurrently. Expected version " + expectedVersion
							+ " but current version is " + currentVersion + ".");
		}
	}

	public static void validateIfMatch(HttpServletRequest request, StrolchRootElement element) {
		if (request != null)
			validateIfMatch(request.getHeader(HttpHeaders.IF_MATCH), element);
	}

	public static Response.ResponseBuilder withETag(Response.ResponseBuilder builder, StrolchRootElement element) {
		if (element != null)
			builder.tag(toEntityTag(element));
		return builder;
	}

	public static Response toResponseWithETag(StrolchRootElement element, Object dto) {
		String json = ChronivaroRestHelper.createGson().toJson(dto);
		Response.ResponseBuilder builder = Response.ok(json, MediaType.APPLICATION_JSON);
		if (element != null)
			builder.tag(toEntityTag(element));
		return builder.build();
	}
}
