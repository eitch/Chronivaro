package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.service.ApprovePeriodService;
import ch.atexxi.chronivaro.core.service.LockPeriodService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.StrolchRestfulConstants;
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

@Path("chronivaro/v1/admin/periods")
public class PeriodResource {

	@POST
	@Path("{id}/approve")
	@Produces(MediaType.APPLICATION_JSON)
	public Response approvePeriod(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new ApprovePeriodService(), new StringArgument(id));
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("{id}/lock")
	@Produces(MediaType.APPLICATION_JSON)
	public Response lockPeriod(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new LockPeriodService(), new StringArgument(id));
		return ChronivaroRestHelper.toResponse(result);
	}
}
