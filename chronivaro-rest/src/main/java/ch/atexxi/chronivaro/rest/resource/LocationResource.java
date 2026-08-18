package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.service.CreateLocationService;
import ch.atexxi.chronivaro.core.service.RemoveLocationService;
import ch.atexxi.chronivaro.core.service.UpdateLocationService;
import ch.atexxi.chronivaro.rest.dto.LocationDto;
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

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.TYPE_LOCATION;
import static ch.atexxi.chronivaro.rest.dto.ChronivaroMapper.locationToDto;

@Path("chronivaro/v1/admin/locations")
public class LocationResource {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getLocations(@Context HttpServletRequest request) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<Resource> locations = tx.streamResources(TYPE_LOCATION).toList();
			List<LocationDto> dtos = locations.stream().map(l -> locationToDto(tx, l)).toList();
			return Response.ok(ChronivaroRestHelper.createGson().toJson(dtos), MediaType.APPLICATION_JSON).build();
		}
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createLocation(@Context HttpServletRequest request, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		LocationDto dto = ChronivaroRestHelper.createGson().fromJson(data, LocationDto.class);

		CreateLocationService.LocationArgument arg = new CreateLocationService.LocationArgument();
		arg.name = dto.name();
		arg.timezone = dto.timezone();
		arg.holidayCalendarId = dto.holidayCalendarId();

		ServiceResult result = serviceHandler.doService(cert, new CreateLocationService(), arg);
		return ChronivaroRestHelper.toResponse(result);
	}

	@PUT
	@Path("{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateLocation(@Context HttpServletRequest request, @PathParam("id") String id, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		LocationDto dto = ChronivaroRestHelper.createGson().fromJson(data, LocationDto.class);

		CreateLocationService.UpdateLocationArgument arg = new CreateLocationService.UpdateLocationArgument();
		arg.id = id;
		arg.name = dto.name();
		arg.timezone = dto.timezone();
		arg.holidayCalendarId = dto.holidayCalendarId();

		ServiceResult result = serviceHandler.doService(cert, new UpdateLocationService(), arg);
		return ChronivaroRestHelper.toResponse(result);
	}

	@DELETE
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response removeLocation(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new RemoveLocationService(), new StringArgument(id));
		return ChronivaroRestHelper.toResponse(result);
	}
}
