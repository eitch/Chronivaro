package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.WorkEntryHelper;
import ch.atexxi.chronivaro.core.service.*;
import ch.atexxi.chronivaro.rest.dto.AbsenceDto;
import ch.atexxi.chronivaro.rest.dto.ChronivaroMapper;
import ch.atexxi.chronivaro.rest.dto.WorkEntryDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.agent.api.StrolchAgent;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.RestfulStrolchComponent;
import li.strolch.rest.helper.ResponseUtil;
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.rest.StrolchRestfulConstants.STROLCH_CERTIFICATE;
import static li.strolch.rest.StrolchRestfulConstants.STROLCH_REMOTE_IP;

@Path("chronivaro/v1")
public class ChronivaroResource {

	@GET
	@Path("presence")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getPresence(@Context HttpServletRequest request, @QueryParam("teamId") String teamId,
			@QueryParam("locationId") String locationId) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();

		PresenceService.PresenceArgument arg = new PresenceService.PresenceArgument();
		arg.teamId = teamId;
		arg.locationId = locationId;

		PresenceService.PresenceResult result = serviceHandler.doService(cert, new PresenceService(), arg);
		if (result.isOk()) {
			return Response
					.ok(ChronivaroRestHelper
									.createGson()
									.toJson(result.presenceInfos.stream().map(ChronivaroMapper::toDto).toList()),
							MediaType.APPLICATION_JSON)
					.build();
		}
		return ResponseUtil.toResponse(result);
	}

	@GET
	@Path("me/work-entries")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMyWorkEntries(@Context HttpServletRequest request, @QueryParam("from") String fromStr,
			@QueryParam("to") String toStr) {

		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUsername());
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
	@Path("me/work-entries")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createWorkEntry(@Context HttpServletRequest request, String data) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		WorkEntryDto dto = ChronivaroRestHelper.createGson().fromJson(data, WorkEntryDto.class);

		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUsername());
			if (employee.isEmpty())
				return Response.status(Response.Status.NOT_FOUND).build();
			employeeId = employee.get().getId();
		}

		AddWorkEntryService.AddWorkEntryArgument arg = new AddWorkEntryService.AddWorkEntryArgument();
		arg.employeeId = employeeId;
		arg.start = dto.start();
		arg.end = dto.end();
		arg.comment = dto.comment();

		ServiceResult result = serviceHandler.doService(cert, new AddWorkEntryService(), arg);
		return ResponseUtil.toResponse(result);
	}

	@PUT
	@Path("me/work-entries/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateWorkEntry(@Context HttpServletRequest request, @PathParam("id") String id, String data) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		WorkEntryDto dto = ChronivaroRestHelper.createGson().fromJson(data, WorkEntryDto.class);

		CorrectWorkEntryService.CorrectWorkEntryArgument arg = new CorrectWorkEntryService.CorrectWorkEntryArgument();
		arg.workEntryId = id;
		arg.start = dto.start();
		arg.end = dto.end();
		arg.comment = dto.comment();

		ServiceResult result = serviceHandler.doService(cert, new CorrectWorkEntryService(), arg);
		return ResponseUtil.toResponse(result);
	}

	@GET
	@Path("me/absences")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMyAbsences(@Context HttpServletRequest request) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUsername());
			if (employee.isEmpty())
				return Response.status(Response.Status.NOT_FOUND).build();
			employeeId = employee.get().getId();
		}

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<Resource> absences = tx.streamResources(TYPE_ABSENCE)
					.filter(a -> a.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.toList();
			List<AbsenceDto> dtos = absences.stream().map(a -> {
				Resource type = tx.getResourceByRelation(a, PARAM_ABSENCE_TYPE, true);
				return ChronivaroMapper.toDto(a, type.getString(PARAM_CODE));
			}).toList();
			return Response.ok(ChronivaroRestHelper.createGson().toJson(dtos), MediaType.APPLICATION_JSON).build();
		}
	}

	@POST
	@Path("me/absences")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response requestAbsence(@Context HttpServletRequest request, String data) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		AbsenceDto dto = ChronivaroRestHelper.createGson().fromJson(data, AbsenceDto.class);

		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUsername());
			if (employee.isEmpty())
				return Response.status(Response.Status.NOT_FOUND).build();
			employeeId = employee.get().getId();
		}

		RequestAbsenceService.RequestAbsenceArgument arg = new RequestAbsenceService.RequestAbsenceArgument();
		arg.employeeId = employeeId;
		arg.absenceTypeCode = dto.absenceTypeCode();
		arg.start = dto.start();
		arg.end = dto.end();
		arg.durationType = dto.durationType();
		arg.dayPart = dto.dayPart();
		arg.minutes = dto.minutes() == null ? 0 : dto.minutes();
		arg.comment = dto.comment();

		ServiceResult result = serviceHandler.doService(cert, new RequestAbsenceService(), arg);
		return ResponseUtil.toResponse(result);
	}
	
	@PUT
	@Path("me/absences/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateAbsence(@Context HttpServletRequest request, @PathParam("id") String id, String data) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		AbsenceDto dto = ChronivaroRestHelper.createGson().fromJson(data, AbsenceDto.class);

		UpdateAbsenceService.UpdateAbsenceArgument arg = new UpdateAbsenceService.UpdateAbsenceArgument();
		arg.absenceId = id;
		arg.absenceTypeCode = dto.absenceTypeCode();
		arg.start = dto.start();
		arg.end = dto.end();
		arg.durationType = dto.durationType();
		arg.dayPart = dto.dayPart();
		arg.minutes = dto.minutes();
		arg.comment = dto.comment();

		ServiceResult result = serviceHandler.doService(cert, new UpdateAbsenceService(), arg);
		return ResponseUtil.toResponse(result);
	}

	@POST
	@Path("me/absences/{id}/cancel")
	@Produces(MediaType.APPLICATION_JSON)
	public Response cancelAbsence(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new CancelAbsenceService(), new StringArgument(id));
		return ResponseUtil.toResponse(result);
	}

	@POST
	@Path("me/timer/start")
	@Produces(MediaType.APPLICATION_JSON)
	public Response startTimer(@Context HttpServletRequest request) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();

		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUsername());
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
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();

		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUsername());
			if (employee.isEmpty())
				return Response.status(Response.Status.NOT_FOUND).build();
			employeeId = employee.get().getId();
		}

		StopTimerService.StopTimerArgument arg = new StopTimerService.StopTimerArgument(employeeId);
		ServiceResult result = serviceHandler.doService(cert, new StopTimerService(), arg);
		return ResponseUtil.toResponse(result);
	}

	@POST
	@Path("me/periods/{id}/submit")
	@Produces(MediaType.APPLICATION_JSON)
	public Response submitPeriod(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new SubmitPeriodService(), new StringArgument(id));
		return ResponseUtil.toResponse(result);
	}

	@POST
	@Path("complete-registration")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response completeRegistration(@Context HttpServletRequest request, String data) {
		CompleteRegistrationService.CompleteRegistrationArgument arg = ChronivaroRestHelper
				.createGson()
				.fromJson(data, CompleteRegistrationService.CompleteRegistrationArgument.class);
		arg.source = (String) request.getAttribute(STROLCH_REMOTE_IP);

		StrolchAgent agent = RestfulStrolchComponent.getInstance().getAgent();
		ServiceResult result;
		try {
			result = agent.runAsAgentWithResult(ctx -> {
				ServiceHandler serviceHandler = agent.getComponent(ServiceHandler.class);
				return serviceHandler.doService(ctx.getCertificate(), new CompleteRegistrationService(), arg);
			});
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return ResponseUtil.toResponse(result);
	}

	@GET
	@Path("me/day-summary/{date}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getDaySummary(@Context HttpServletRequest request, @PathParam("date") String dateStr) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();

		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUsername());
			if (employee.isEmpty())
				return Response.status(Response.Status.NOT_FOUND).build();
			employeeId = employee.get().getId();
		}

		DaySummaryService.DaySummaryArgument arg = new DaySummaryService.DaySummaryArgument();
		arg.employeeId = employeeId;
		arg.date = LocalDate.parse(dateStr);

		DaySummaryService.DaySummaryResult result = serviceHandler.doService(cert, new DaySummaryService(), arg);
		if (result.isOk()) {
			return Response
					.ok(ChronivaroRestHelper.createGson().toJson(ChronivaroMapper.toDto(result.daySummary)),
							MediaType.APPLICATION_JSON)
					.build();
		}
		return ResponseUtil.toResponse(result);
	}

	@GET
	@Path("me/month-summary/{yearMonth}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMonthSummary(@Context HttpServletRequest request, @PathParam("yearMonth") String yearMonthStr) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();

		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUsername());
			if (employee.isEmpty())
				return Response.status(Response.Status.NOT_FOUND).build();
			employeeId = employee.get().getId();
		}

		MonthSummaryService.MonthSummaryArgument arg = new MonthSummaryService.MonthSummaryArgument();
		arg.employeeId = employeeId;
		arg.yearMonth = YearMonth.parse(yearMonthStr);

		MonthSummaryService.MonthSummaryResult result = serviceHandler.doService(cert, new MonthSummaryService(), arg);
		if (result.isOk()) {
			return Response
					.ok(ChronivaroRestHelper.createGson().toJson(ChronivaroMapper.toDto(result.monthSummary)),
							MediaType.APPLICATION_JSON)
					.build();
		}
		return ResponseUtil.toResponse(result);
	}
}
