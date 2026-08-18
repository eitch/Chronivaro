package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.service.ApprovePeriodService;
import ch.atexxi.chronivaro.core.service.LockPeriodService;
import ch.atexxi.chronivaro.core.service.PeriodActionArgument;
import ch.atexxi.chronivaro.core.service.RejectPeriodService;
import ch.atexxi.chronivaro.core.service.ReopenPeriodService;
import ch.atexxi.chronivaro.rest.dto.ChronivaroMapper;
import ch.atexxi.chronivaro.rest.dto.PeriodActionRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.StrolchRestfulConstants;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.TYPE_TIME_PERIOD;
import static li.strolch.utils.helper.StringHelper.isEmpty;
import static li.strolch.utils.helper.StringHelper.isNotEmpty;

@Path("chronivaro/v1/admin/periods")
public class AdminPeriodResource {

	@POST
	@Path("{id}/approve")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response approvePeriod(
			@Context HttpServletRequest request,
			@PathParam("id") String id,
			String data) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, id, true);
			ConcurrencyHelper.validateIfMatch(request, period);
		}

		PeriodActionRequestDto dto = isNotEmpty(data) ?
				ChronivaroRestHelper.createGson().fromJson(data, PeriodActionRequestDto.class) : null;

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		String comment = dto != null ? dto.comment() : null;
		ServiceResult result = serviceHandler.doService(cert, new ApprovePeriodService(),
				new PeriodActionArgument(id, comment));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, id, true);
				return ConcurrencyHelper.toResponseWithETag(period, ChronivaroMapper.periodToDto(period));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("{id}/reject")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response rejectPeriod(
			@Context HttpServletRequest request,
			@PathParam("id") String id,
			String data) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		PeriodActionRequestDto dto = isNotEmpty(data) ?
				ChronivaroRestHelper.createGson().fromJson(data, PeriodActionRequestDto.class) : null;

		String comment = dto != null ? dto.comment() : null;
		if (isEmpty(comment)) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "VALIDATION_ERROR",
					"Rejection reason must be provided in comment");
		}

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, id, true);
			ConcurrencyHelper.validateIfMatch(request, period);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new RejectPeriodService(),
				new PeriodActionArgument(id, comment));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, id, true);
				return ConcurrencyHelper.toResponseWithETag(period, ChronivaroMapper.periodToDto(period));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("{id}/reopen")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response reopenPeriod(
			@Context HttpServletRequest request,
			@PathParam("id") String id,
			String data) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		PeriodActionRequestDto dto = isNotEmpty(data) ?
				ChronivaroRestHelper.createGson().fromJson(data, PeriodActionRequestDto.class) : null;

		String comment = dto != null ? dto.comment() : null;
		if (isEmpty(comment)) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "VALIDATION_ERROR",
					"Reopen reason must be provided in comment");
		}

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, id, true);
			ConcurrencyHelper.validateIfMatch(request, period);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new ReopenPeriodService(),
				new PeriodActionArgument(id, comment));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, id, true);
				return ConcurrencyHelper.toResponseWithETag(period, ChronivaroMapper.periodToDto(period));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("{id}/lock")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response lockPeriod(
			@Context HttpServletRequest request,
			@PathParam("id") String id,
			String data) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, id, true);
			ConcurrencyHelper.validateIfMatch(request, period);
		}

		PeriodActionRequestDto dto = isNotEmpty(data) ?
				ChronivaroRestHelper.createGson().fromJson(data, PeriodActionRequestDto.class) : null;

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		String comment = dto != null ? dto.comment() : null;
		ServiceResult result = serviceHandler.doService(cert, new LockPeriodService(),
				new PeriodActionArgument(id, comment));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, id, true);
				return ConcurrencyHelper.toResponseWithETag(period, ChronivaroMapper.periodToDto(period));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}
}
