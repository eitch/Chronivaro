package ch.eitchnet.chronivaro.rest.resource;

import ch.eitchnet.chronivaro.core.search.AuditEventSearch;
import ch.eitchnet.chronivaro.rest.dto.ChronivaroMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.StrolchRestfulConstants;
import li.strolch.search.SearchResult;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.BAG_PARAMETERS;
import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.PARAM_DATE;
import static li.strolch.utils.helper.StringHelper.isEmpty;

@Path("chronivaro/v1/admin/audit-logs")
public class AuditLogsResource {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAuditLogs(@Context HttpServletRequest request, @QueryParam("offset") Integer offset,
			@QueryParam("limit") Integer limit, @QueryParam("entityType") String entityType,
			@QueryParam("entityId") String entityId, @QueryParam("username") String username,
			@QueryParam("action") String action, @QueryParam("from") String fromStr, @QueryParam("to") String toStr) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		ZonedDateTime from = parseDateTime(fromStr, false);
		ZonedDateTime to = parseDateTime(toStr, true);

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			SearchResult<Resource> searchResult = new AuditEventSearch()
					.forElementType(entityType)
					.forElementId(entityId)
					.forUsername(username)
					.forAction(action)
					.inDateRange(from, to)
					.search(tx)
					.orderByParam(BAG_PARAMETERS, PARAM_DATE, true);

			return PaginationHelper.toPagedOrListResponse(searchResult, offset, limit, ChronivaroMapper::auditLogToDto);
		}
	}

	private static ZonedDateTime parseDateTime(String str, boolean isEnd) {
		if (isEmpty(str))
			return null;
		try {
			return ZonedDateTime.parse(str);
		} catch (DateTimeParseException e1) {
			try {
				LocalDate d = LocalDate.parse(str);
				return isEnd ? d.atTime(23, 59, 59, 999_999_999).atZone(ZoneId.of("Europe/Zurich")) :
						d.atStartOfDay(ZoneId.of("Europe/Zurich"));
			} catch (DateTimeParseException e2) {
				throw new RestException(Response.Status.BAD_REQUEST, "INVALID_DATE_FORMAT",
						"Invalid date/time format for: " + str);
			}
		}
	}
}
