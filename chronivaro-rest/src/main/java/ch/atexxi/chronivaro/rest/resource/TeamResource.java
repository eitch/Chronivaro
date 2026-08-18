package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.service.CreateTeamService;
import ch.atexxi.chronivaro.core.service.RemoveTeamService;
import ch.atexxi.chronivaro.core.service.UpdateTeamService;
import ch.atexxi.chronivaro.rest.dto.ChronivaroMapper;
import ch.atexxi.chronivaro.rest.dto.TeamDto;
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

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.TYPE_TEAM;

@Path("chronivaro/v1/admin/teams")
public class TeamResource {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getTeams(@Context HttpServletRequest request, @QueryParam("offset") Integer offset,
			@QueryParam("limit") Integer limit) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<Resource> teams = tx.streamResources(TYPE_TEAM).toList();
			return PaginationHelper.toPagedOrListResponse(teams, offset, limit, ChronivaroMapper::teamToDto);
		}
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createTeam(@Context HttpServletRequest request, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		TeamDto dto = ChronivaroRestHelper.createGson().fromJson(data, TeamDto.class);

		CreateTeamService.TeamArgument arg = new CreateTeamService.TeamArgument();
		arg.name = dto.name();

		ServiceResult result = serviceHandler.doService(cert, new CreateTeamService(), arg);
		return ChronivaroRestHelper.toResponse(result);
	}

	@PUT
	@Path("{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateTeam(@Context HttpServletRequest request, @PathParam("id") String id, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		TeamDto dto = ChronivaroRestHelper.createGson().fromJson(data, TeamDto.class);

		CreateTeamService.UpdateTeamArgument arg = new CreateTeamService.UpdateTeamArgument();
		arg.id = id;
		arg.name = dto.name();

		ServiceResult result = serviceHandler.doService(cert, new UpdateTeamService(), arg);
		return ChronivaroRestHelper.toResponse(result);
	}

	@DELETE
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response removeTeam(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new RemoveTeamService(), new StringArgument(id));
		return ChronivaroRestHelper.toResponse(result);
	}
}
