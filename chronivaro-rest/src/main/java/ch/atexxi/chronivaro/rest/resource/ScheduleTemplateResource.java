package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.service.CreateScheduleTemplateService;
import ch.atexxi.chronivaro.core.service.RemoveScheduleTemplateService;
import ch.atexxi.chronivaro.core.service.UpdateScheduleTemplateService;
import ch.atexxi.chronivaro.rest.dto.ChronivaroMapper;
import ch.atexxi.chronivaro.rest.dto.ScheduleTemplateDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.StrolchRestfulConstants;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceResult;

import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE;

@Path("chronivaro/v1/admin/schedule-templates")
public class ScheduleTemplateResource {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getTemplates(@Context HttpServletRequest request, @QueryParam("offset") Integer offset,
			@QueryParam("limit") Integer limit) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<Resource> templates = tx.streamResources(TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE).toList();
			return PaginationHelper.toPagedOrListResponse(templates, offset, limit, ChronivaroMapper::scheduleTemplateToDto);
		}
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createTemplate(@Context HttpServletRequest request, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		CreateScheduleTemplateService.CreateScheduleTemplateArgument arg = ChronivaroRestHelper
				.createGson()
				.fromJson(data, CreateScheduleTemplateService.CreateScheduleTemplateArgument.class);
		ServiceResult result = serviceHandler.doService(cert, new CreateScheduleTemplateService(), arg);
		return ChronivaroRestHelper.toResponse(result);
	}

	@PUT
	@Path("{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateTemplate(@Context HttpServletRequest request, @PathParam("id") String id, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		UpdateScheduleTemplateService.UpdateScheduleTemplateArgument arg = ChronivaroRestHelper
				.createGson()
				.fromJson(data, UpdateScheduleTemplateService.UpdateScheduleTemplateArgument.class);
		arg.id = id;
		ServiceResult result = serviceHandler.doService(cert, new UpdateScheduleTemplateService(), arg);
		return ChronivaroRestHelper.toResponse(result);
	}

	@DELETE
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response removeTemplate(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new RemoveScheduleTemplateService(),
				new StringArgument(id));
		return ChronivaroRestHelper.toResponse(result);
	}
}
