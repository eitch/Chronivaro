package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.WorkEntryHelper;
import ch.atexxi.chronivaro.core.service.DaySummaryService;
import ch.atexxi.chronivaro.core.service.MonthSummaryService;
import ch.atexxi.chronivaro.core.service.StartTimerService;
import ch.atexxi.chronivaro.core.service.StopTimerService;
import ch.atexxi.chronivaro.rest.dto.ChronivaroMapper;
import ch.atexxi.chronivaro.rest.dto.WorkEntryDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.StrolchRestfulConstants;
import li.strolch.rest.helper.ResponseUtil;
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Path("chronivaro/v1")
public class ChronivaroResource {

	@GET
	@Path("me/work-entries")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMyWorkEntries(@Context HttpServletRequest request,
			@QueryParam("from") String fromStr,
			@QueryParam("to") String toStr) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return Response.status(Response.Status.NOT_FOUND).build();
			employeeId = employee.get().getId();
		}

		ZonedDateTime from = ZonedDateTime.parse(fromStr);
		ZonedDateTime to = ZonedDateTime.parse(toStr);

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<Resource> entries = WorkEntryHelper.findWorkEntries(tx, employeeId, from, to);
			List<WorkEntryDto> dtos = entries.stream().map(ChronivaroMapper::toDto).toList();
			return Response.ok(ChronivaroRestHelper.createGson().toJson(dtos), MediaType.APPLICATION_JSON).build();
		}
	}

	@POST
	@Path("me/timer/start")
	@Produces(MediaType.APPLICATION_JSON)
	public Response startTimer(@Context HttpServletRequest request) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();

		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return Response.status(Response.Status.NOT_FOUND).build();
			employeeId = employee.get().getId();
		}

		StringArgument arg = new StringArgument(employeeId);
		ServiceResult result = serviceHandler.doService(cert, new StartTimerService(), arg);
		return ResponseUtil.toResponse(result);
	}

	@POST
	@Path("me/timer/stop")
	@Produces(MediaType.APPLICATION_JSON)
	public Response stopTimer(@Context HttpServletRequest request) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();

		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return Response.status(Response.Status.NOT_FOUND).build();
			employeeId = employee.get().getId();
		}

		StringArgument arg = new StringArgument(employeeId);
		ServiceResult result = serviceHandler.doService(cert, new StopTimerService(), arg);
		return ResponseUtil.toResponse(result);
	}

	@GET
	@Path("me/day-summary/{date}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getDaySummary(@Context HttpServletRequest request, @PathParam("date") String dateStr) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();

		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return Response.status(Response.Status.NOT_FOUND).build();
			employeeId = employee.get().getId();
		}

		DaySummaryService.DaySummaryArgument arg = new DaySummaryService.DaySummaryArgument();
		arg.employeeId = employeeId;
		arg.date = LocalDate.parse(dateStr);

		DaySummaryService.DaySummaryResult result = serviceHandler.doService(cert, new DaySummaryService(), arg);
		if (result.isOk()) {
			return Response.ok(ChronivaroRestHelper.createGson().toJson(ChronivaroMapper.toDto(result.daySummary)), MediaType.APPLICATION_JSON).build();
		}
		return ResponseUtil.toResponse(result);
	}

	@GET
	@Path("me/month-summary/{yearMonth}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMonthSummary(@Context HttpServletRequest request, @PathParam("yearMonth") String yearMonthStr) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();

		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return Response.status(Response.Status.NOT_FOUND).build();
			employeeId = employee.get().getId();
		}

		MonthSummaryService.MonthSummaryArgument arg = new MonthSummaryService.MonthSummaryArgument();
		arg.employeeId = employeeId;
		arg.yearMonth = YearMonth.parse(yearMonthStr);

		MonthSummaryService.MonthSummaryResult result = serviceHandler.doService(cert, new MonthSummaryService(), arg);
		if (result.isOk()) {
			return Response.ok(ChronivaroRestHelper.createGson().toJson(ChronivaroMapper.toDto(result.monthSummary)), MediaType.APPLICATION_JSON).build();
		}
		return ResponseUtil.toResponse(result);
	}
}
