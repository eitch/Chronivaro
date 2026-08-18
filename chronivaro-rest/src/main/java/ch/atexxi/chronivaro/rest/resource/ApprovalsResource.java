package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.search.TimePeriodSearch;
import ch.atexxi.chronivaro.core.service.ApproveAbsenceService;
import ch.atexxi.chronivaro.core.service.ApprovePeriodService;
import ch.atexxi.chronivaro.core.service.PeriodActionArgument;
import ch.atexxi.chronivaro.core.service.RejectAbsenceService;
import ch.atexxi.chronivaro.core.service.RejectPeriodService;
import ch.atexxi.chronivaro.rest.dto.ChronivaroMapper;
import ch.atexxi.chronivaro.rest.dto.PeriodActionRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
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
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.utils.helper.StringHelper.isEmpty;
import static li.strolch.utils.helper.StringHelper.isNotEmpty;

@Path("chronivaro/v1/approvals")
public class ApprovalsResource {

	@GET
	@Path("periods")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getSubmittedPeriods(
			@Context HttpServletRequest request,
			@QueryParam("offset") Integer offset,
			@QueryParam("limit") Integer limit) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			SearchResult<Resource> searchResult = new TimePeriodSearch()
					.forState(STATE_SUBMITTED)
					.search(tx);

			return PaginationHelper.toPagedOrListResponse(searchResult, offset, limit, ChronivaroMapper::periodToDto);
		}
	}

	@POST
	@Path("periods/{id}/approve")
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
	@Path("periods/{id}/reject")
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

	@GET
	@Path("absences")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getSubmittedAbsences(
			@Context HttpServletRequest request,
			@QueryParam("offset") Integer offset,
			@QueryParam("limit") Integer limit) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<Resource> submittedAbsences = tx.streamResources(TYPE_ABSENCE)
					.filter(a -> STATE_SUBMITTED.equals(a.getString(PARAM_STATE)))
					.toList();

			return PaginationHelper.toPagedOrListResponse(submittedAbsences, offset, limit, absence -> {
				Resource type = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
				return ChronivaroMapper.toDto(absence, type.getString(PARAM_CODE));
			});
		}
	}

	@POST
	@Path("absences/{id}/approve")
	@Produces(MediaType.APPLICATION_JSON)
	public Response approveAbsence(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
			ConcurrencyHelper.validateIfMatch(request, absence);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new ApproveAbsenceService(), new StringArgument(id));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
				Resource type = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
				return ConcurrencyHelper.toResponseWithETag(absence, ChronivaroMapper.toDto(absence, type.getString(PARAM_CODE)));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("absences/{id}/reject")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response rejectAbsence(
			@Context HttpServletRequest request,
			@PathParam("id") String id,
			String data) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
			ConcurrencyHelper.validateIfMatch(request, absence);
		}

		PeriodActionRequestDto dto = isNotEmpty(data) ?
				ChronivaroRestHelper.createGson().fromJson(data, PeriodActionRequestDto.class) : null;

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		String comment = dto != null ? dto.comment() : null;
		ServiceResult result = serviceHandler.doService(cert, new RejectAbsenceService(),
				new RejectAbsenceService.RejectAbsenceArgument(id, comment));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
				Resource type = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
				return ConcurrencyHelper.toResponseWithETag(absence, ChronivaroMapper.toDto(absence, type.getString(PARAM_CODE)));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}
}
