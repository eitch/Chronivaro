package ch.eitchnet.chronivaro.rest.resource;

import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.search.OnCallPeriodSearch;
import ch.eitchnet.chronivaro.core.service.CreateOnCallPeriodService;
import ch.eitchnet.chronivaro.core.service.RemoveOnCallPeriodService;
import ch.eitchnet.chronivaro.core.service.UpdateOnCallPeriodService;
import ch.eitchnet.chronivaro.rest.dto.ChronivaroMapper;
import ch.eitchnet.chronivaro.rest.dto.OnCallPeriodDto;
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

import java.time.LocalDate;
import java.util.List;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.TYPE_ON_CALL_PERIOD;

@Path("chronivaro/v1")
public class OnCallPeriodResource {

	public record CreateOnCallPeriodRequest(
			String employeeId,
			LocalDate startDate,
			String startTime,
			LocalDate endDate,
			String endTime,
			String comment
	) {
	}

	public record UpdateOnCallPeriodRequest(
			LocalDate startDate,
			String startTime,
			LocalDate endDate,
			String endTime,
			String comment
	) {
	}

	@GET
	@Path("admin/on-call-periods")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAdminOnCallPeriods(
			@Context HttpServletRequest request,
			@QueryParam("employeeId") String employeeId,
			@QueryParam("from") String fromStr,
			@QueryParam("to") String toStr) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		LocalDate from = fromStr != null && !fromStr.isBlank() ? LocalDate.parse(fromStr) : null;
		LocalDate to = toStr != null && !toStr.isBlank() ? LocalDate.parse(toStr) : null;

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<Resource> periods = new OnCallPeriodSearch()
					.forEmployee(employeeId)
					.between(from, to)
					.searchPeriods(tx);

			List<OnCallPeriodDto> dtos = periods.stream()
					.map(p -> ChronivaroMapper.onCallPeriodToDto(tx, p))
					.toList();
			return Response.ok(ChronivaroRestHelper.createGson().toJson(dtos), MediaType.APPLICATION_JSON).build();
		}
	}

	@POST
	@Path("admin/on-call-periods")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createOnCallPeriod(@Context HttpServletRequest request, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		CreateOnCallPeriodRequest req = ChronivaroRestHelper.createGson().fromJson(data, CreateOnCallPeriodRequest.class);

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		CreateOnCallPeriodService.CreateOnCallPeriodArgument arg = new CreateOnCallPeriodService.CreateOnCallPeriodArgument();
		arg.employeeId = req.employeeId();
		arg.startDate = req.startDate();
		arg.startTime = req.startTime();
		arg.endDate = req.endDate();
		arg.endTime = req.endTime();
		arg.comment = req.comment();

		ServiceResult result = serviceHandler.doService(cert, new CreateOnCallPeriodService(), arg);
		return ChronivaroRestHelper.toResponse(result);
	}

	@PUT
	@Path("admin/on-call-periods/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateOnCallPeriod(@Context HttpServletRequest request, @PathParam("id") String id, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource period = tx.getResourceBy(TYPE_ON_CALL_PERIOD, id, true);
			ConcurrencyHelper.validateIfMatch(request, period);
		}

		UpdateOnCallPeriodRequest req = ChronivaroRestHelper.createGson().fromJson(data, UpdateOnCallPeriodRequest.class);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		UpdateOnCallPeriodService.UpdateOnCallPeriodArgument arg = new UpdateOnCallPeriodService.UpdateOnCallPeriodArgument();
		arg.id = id;
		arg.startDate = req.startDate();
		arg.startTime = req.startTime();
		arg.endDate = req.endDate();
		arg.endTime = req.endTime();
		arg.comment = req.comment();

		ServiceResult result = serviceHandler.doService(cert, new UpdateOnCallPeriodService(), arg);
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource period = tx.getResourceBy(TYPE_ON_CALL_PERIOD, id, true);
				return ConcurrencyHelper.toResponseWithETag(period, ChronivaroMapper.onCallPeriodToDto(tx, period));
			}
		}

		return ChronivaroRestHelper.toResponse(result);
	}

	@DELETE
	@Path("admin/on-call-periods/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deleteOnCallPeriod(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource period = tx.getResourceBy(TYPE_ON_CALL_PERIOD, id, true);
			ConcurrencyHelper.validateIfMatch(request, period);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		StringArgument arg = new StringArgument();
		arg.value = id;

		ServiceResult result = serviceHandler.doService(cert, new RemoveOnCallPeriodService(), arg);
		return ChronivaroRestHelper.toResponse(result);
	}

	@GET
	@Path("me/on-call-periods")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMyOnCallPeriods(
			@Context HttpServletRequest request,
			@QueryParam("from") String fromStr,
			@QueryParam("to") String toStr) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		LocalDate from = fromStr != null && !fromStr.isBlank() ? LocalDate.parse(fromStr) : null;
		LocalDate to = toStr != null && !toStr.isBlank() ? LocalDate.parse(toStr) : null;

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource employee = ChronivaroModelHelper.getEmployee(tx, cert.getUsername());
			List<Resource> periods = new OnCallPeriodSearch()
					.forEmployee(employee.getId())
					.between(from, to)
					.searchPeriods(tx);

			List<OnCallPeriodDto> dtos = periods.stream()
					.map(p -> ChronivaroMapper.onCallPeriodToDto(tx, p))
					.toList();
			return Response.ok(ChronivaroRestHelper.createGson().toJson(dtos), MediaType.APPLICATION_JSON).build();
		}
	}

	@GET
	@Path("employees/{id}/on-call-periods")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEmployeeOnCallPeriods(
			@Context HttpServletRequest request,
			@PathParam("id") String employeeId,
			@QueryParam("from") String fromStr,
			@QueryParam("to") String toStr) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		LocalDate from = fromStr != null && !fromStr.isBlank() ? LocalDate.parse(fromStr) : null;
		LocalDate to = toStr != null && !toStr.isBlank() ? LocalDate.parse(toStr) : null;

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<Resource> periods = new OnCallPeriodSearch()
					.forEmployee(employeeId)
					.between(from, to)
					.searchPeriods(tx);

			List<OnCallPeriodDto> dtos = periods.stream()
					.map(p -> ChronivaroMapper.onCallPeriodToDto(tx, p))
					.toList();
			return Response.ok(ChronivaroRestHelper.createGson().toJson(dtos), MediaType.APPLICATION_JSON).build();
		}
	}
}
