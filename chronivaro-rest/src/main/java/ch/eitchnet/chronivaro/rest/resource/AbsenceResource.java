package ch.eitchnet.chronivaro.rest.resource;

import ch.eitchnet.chronivaro.core.service.ApproveAbsenceService;
import ch.eitchnet.chronivaro.core.service.CancelAbsenceService;
import ch.eitchnet.chronivaro.core.service.RejectAbsenceService;
import ch.eitchnet.chronivaro.rest.dto.AbsenceDto;
import ch.eitchnet.chronivaro.rest.dto.ChronivaroMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

import java.util.List;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.rest.StrolchRestfulConstants.STROLCH_CERTIFICATE;

@Path("chronivaro/v1/admin/absences")
public class AbsenceResource {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAbsences(@Context HttpServletRequest request, @QueryParam("offset") Integer offset,
			@QueryParam("limit") Integer limit) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<Resource> absences = tx.streamResources(TYPE_ABSENCE).toList();
			return PaginationHelper.toPagedOrListResponse(absences, offset, limit, a -> {
				Resource type = tx.getResourceByRelation(a, PARAM_ABSENCE_TYPE, true);
				return ChronivaroMapper.toDto(tx, a, type);
			});
		}
	}

	@GET
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAbsence(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
			Resource type = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
			return ConcurrencyHelper.toResponseWithETag(absence, ChronivaroMapper.toDto(tx, absence, type));
		}
	}

	@POST
	@Path("{id}/approve")
	@Produces(MediaType.APPLICATION_JSON)
	public Response approveAbsence(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
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
				return ConcurrencyHelper.toResponseWithETag(absence, ChronivaroMapper.toDto(tx, absence, type));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("{id}/reject")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response rejectAbsence(@Context HttpServletRequest request, @PathParam("id") String id, String data) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
			ConcurrencyHelper.validateIfMatch(request, absence);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		RejectAbsenceService.RejectAbsenceArgument arg = ChronivaroRestHelper
				.createGson()
				.fromJson(data, RejectAbsenceService.RejectAbsenceArgument.class);
		arg.absenceId = id;
		ServiceResult result = serviceHandler.doService(cert, new RejectAbsenceService(), arg);
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
				Resource type = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
				return ConcurrencyHelper.toResponseWithETag(absence, ChronivaroMapper.toDto(tx, absence, type));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("{id}/cancel")
	@Consumes({MediaType.APPLICATION_JSON, MediaType.WILDCARD})
	@Produces(MediaType.APPLICATION_JSON)
	public Response cancelAbsence(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
			ConcurrencyHelper.validateIfMatch(request, absence);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new CancelAbsenceService(), new StringArgument(id));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
				Resource type = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
				return ConcurrencyHelper.toResponseWithETag(absence, ChronivaroMapper.toDto(tx, absence, type));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}
}
