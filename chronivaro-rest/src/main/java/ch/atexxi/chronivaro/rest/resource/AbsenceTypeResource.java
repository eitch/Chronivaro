package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.service.CreateAbsenceTypeService;
import ch.atexxi.chronivaro.core.service.RemoveAbsenceTypeService;
import ch.atexxi.chronivaro.core.service.UpdateAbsenceTypeService;
import ch.atexxi.chronivaro.rest.dto.AbsenceTypeDto;
import ch.atexxi.chronivaro.rest.dto.ChronivaroMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.StrolchRestfulConstants;
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.TYPE_ABSENCE_TYPE;

@Path("chronivaro/v1/admin/absence-types")
public class AbsenceTypeResource {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAbsenceTypes(@Context HttpServletRequest request, @QueryParam("offset") Integer offset,
			@QueryParam("limit") Integer limit) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<Resource> types = tx.streamResources(TYPE_ABSENCE_TYPE).toList();
			return PaginationHelper.toPagedOrListResponse(types, offset, limit, ChronivaroMapper::absenceTypeToDto);
		}
	}

	@GET
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAbsenceType(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource type = tx.getResourceBy(TYPE_ABSENCE_TYPE, id, true);
			return ConcurrencyHelper.toResponseWithETag(type, ChronivaroMapper.absenceTypeToDto(type));
		}
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createAbsenceType(@Context HttpServletRequest request, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		AbsenceTypeDto dto = ChronivaroRestHelper.createGson().fromJson(data, AbsenceTypeDto.class);

		CreateAbsenceTypeService.AbsenceTypeArgument arg = new CreateAbsenceTypeService.AbsenceTypeArgument();
		arg.code = dto.code();
		arg.name = dto.name();
		arg.countAsTargetTime = dto.countAsTargetTime();
		arg.reduceVacationCredit = dto.reduceVacationCredit();
		arg.paid = dto.paid();
		arg.approvalRequired = dto.approvalRequired();
		arg.durationTypes = dto.durationTypes();
		arg.active = dto.active();

		ServiceResult result = serviceHandler.doService(cert, new CreateAbsenceTypeService(), arg);
		return ChronivaroRestHelper.toResponse(result);
	}

	@PUT
	@Path("{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateAbsenceType(@Context HttpServletRequest request, @PathParam("id") String id, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource type = tx.getResourceBy(TYPE_ABSENCE_TYPE, id, true);
			ConcurrencyHelper.validateIfMatch(request, type);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		AbsenceTypeDto dto = ChronivaroRestHelper.createGson().fromJson(data, AbsenceTypeDto.class);

		CreateAbsenceTypeService.UpdateAbsenceTypeArgument arg
				= new CreateAbsenceTypeService.UpdateAbsenceTypeArgument();
		arg.id = id;
		arg.code = dto.code();
		arg.name = dto.name();
		arg.countAsTargetTime = dto.countAsTargetTime();
		arg.reduceVacationCredit = dto.reduceVacationCredit();
		arg.paid = dto.paid();
		arg.approvalRequired = dto.approvalRequired();
		arg.durationTypes = dto.durationTypes();
		arg.active = dto.active();

		ServiceResult result = serviceHandler.doService(cert, new UpdateAbsenceTypeService(), arg);
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource type = tx.getResourceBy(TYPE_ABSENCE_TYPE, id, true);
				return ConcurrencyHelper.toResponseWithETag(type, ChronivaroMapper.absenceTypeToDto(type));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@DELETE
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response removeAbsenceType(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource type = tx.getResourceBy(TYPE_ABSENCE_TYPE, id, true);
			ConcurrencyHelper.validateIfMatch(request, type);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new RemoveAbsenceTypeService(), new StringArgument(id));
		return ChronivaroRestHelper.toResponse(result);
	}
}
