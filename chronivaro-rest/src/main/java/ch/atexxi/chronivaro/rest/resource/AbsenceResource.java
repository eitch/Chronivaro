package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.service.ApproveAbsenceService;
import ch.atexxi.chronivaro.rest.dto.AbsenceDto;
import ch.atexxi.chronivaro.rest.dto.ChronivaroMapper;
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

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
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
				return ChronivaroMapper.toDto(a, type.getString(PARAM_CODE));
			});
		}
	}

	@POST
	@Path("{id}/approve")
	@Produces(MediaType.APPLICATION_JSON)
	public Response approveAbsence(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new ApproveAbsenceService(), new StringArgument(id));
		return ChronivaroRestHelper.toResponse(result);
	}
}
