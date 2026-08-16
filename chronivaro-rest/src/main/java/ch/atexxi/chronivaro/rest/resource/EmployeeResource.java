package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.service.*;
import ch.atexxi.chronivaro.rest.dto.ChronivaroMapper;
import ch.atexxi.chronivaro.rest.dto.EmployeeDto;
import ch.atexxi.chronivaro.rest.dto.ScheduleDto;
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
import li.strolch.service.StringResult;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.rest.dto.ChronivaroMapper.employeeToDto;

@Path("chronivaro/v1/admin/employees")
public class EmployeeResource {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEmployees(@Context HttpServletRequest request) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<Resource> employees = tx.streamResources(TYPE_EMPLOYEE).toList();
			List<EmployeeDto> dtos = employees.stream().map(e -> employeeToDto(tx, e)).toList();
			return Response.ok(ChronivaroRestHelper.createGson().toJson(dtos), MediaType.APPLICATION_JSON).build();
		}
	}

	@GET
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEmployee(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, id);
			if (employee == null)
				return Response.status(Response.Status.NOT_FOUND).build();
			return Response
					.ok(ChronivaroRestHelper.createGson().toJson(employeeToDto(tx, employee)),
							MediaType.APPLICATION_JSON)
					.build();
		}
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createEmployee(@Context HttpServletRequest request, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		EmployeeDto dto = ChronivaroRestHelper.createGson().fromJson(data, EmployeeDto.class);

		CreateEmployeeService.EmployeeArgument arg = new CreateEmployeeService.EmployeeArgument();
		arg.personalNumber = dto.personalNumber();
		arg.firstname = dto.firstname();
		arg.lastname = dto.lastname();
		arg.birthdate = dto.birthdate();
		arg.teamId = dto.teamId();
		arg.locationId = dto.locationId();
		arg.timezone = dto.timezone();
		arg.joinDate = dto.joinDate();
		arg.exitDate = dto.exitDate();
		arg.active = dto.active();
		arg.username = dto.username();
		arg.email = dto.email();
		arg.scheduleTemplateId = dto.scheduleTemplateId();

		StringResult result = serviceHandler.doService(cert, new CreateEmployeeService(), arg);
		if (result.isNok())
			return ResponseUtil.toResponse(result);

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, result.getValue(), true);
			return Response
					.ok(ChronivaroRestHelper.createGson().toJson(employeeToDto(tx, employee)),
							MediaType.APPLICATION_JSON)
					.build();
		}
	}

	@PUT
	@Path("{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateEmployee(@Context HttpServletRequest request, @PathParam("id") String id, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		EmployeeDto dto = ChronivaroRestHelper.createGson().fromJson(data, EmployeeDto.class);

		CreateEmployeeService.UpdateEmployeeArgument arg = new CreateEmployeeService.UpdateEmployeeArgument();
		arg.id = id;
		arg.personalNumber = dto.personalNumber();
		arg.firstname = dto.firstname();
		arg.lastname = dto.lastname();
		arg.birthdate = dto.birthdate();
		arg.teamId = dto.teamId();
		arg.locationId = dto.locationId();
		arg.timezone = dto.timezone();
		arg.joinDate = dto.joinDate();
		arg.exitDate = dto.exitDate();
		arg.active = dto.active();
		arg.email = dto.email();
		arg.username = dto.username();

		ServiceResult result = serviceHandler.doService(cert, new UpdateEmployeeService(), arg);
		return ResponseUtil.toResponse(result);
	}

	@DELETE
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response removeEmployee(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new RemoveEmployeeService(), new StringArgument(id));
		return ResponseUtil.toResponse(result);
	}

	@POST
	@Path("{id}/schedules")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createSchedule(@Context HttpServletRequest request, @PathParam("id") String id, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		CreateScheduleService.CreateScheduleArgument arg = ChronivaroRestHelper
				.createGson()
				.fromJson(data, CreateScheduleService.CreateScheduleArgument.class);
		arg.employeeId = id;
		ServiceResult result = serviceHandler.doService(cert, new CreateScheduleService(), arg);
		return ResponseUtil.toResponse(result);
	}

	@GET
	@Path("{id}/schedules")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getSchedules(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<Resource> schedules = tx
					.streamResources(TYPE_EMPLOYMENT_SCHEDULE)
					.filter(s -> s.getRelationId(PARAM_EMPLOYEE).equals(id))
					.toList();
			List<ScheduleDto> dtos = schedules.stream().map(ChronivaroMapper::scheduleToDto).toList();
			return Response.ok(ChronivaroRestHelper.createGson().toJson(dtos), MediaType.APPLICATION_JSON).build();
		}
	}

	@PUT
	@Path("{id}/schedules/{scheduleId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateSchedule(@Context HttpServletRequest request, @PathParam("id") String id,
			@PathParam("scheduleId") String scheduleId, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		UpdateScheduleService.UpdateScheduleArgument arg = ChronivaroRestHelper
				.createGson()
				.fromJson(data, UpdateScheduleService.UpdateScheduleArgument.class);
		arg.id = scheduleId;
		ServiceResult result = serviceHandler.doService(cert, new UpdateScheduleService(), arg);
		return ResponseUtil.toResponse(result);
	}

	@DELETE
	@Path("{id}/schedules/{scheduleId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response removeSchedule(@Context HttpServletRequest request, @PathParam("id") String id,
			@PathParam("scheduleId") String scheduleId) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new RemoveScheduleService(),
				new StringArgument(scheduleId));
		return ResponseUtil.toResponse(result);
	}

	@POST
	@Path("{id}/vacation-corrections")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response addVacationCorrection(@Context HttpServletRequest request, @PathParam("id") String id,
			String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		AddVacationCorrectionService.AddVacationCorrectionArgument arg = ChronivaroRestHelper
				.createGson()
				.fromJson(data, AddVacationCorrectionService.AddVacationCorrectionArgument.class);
		arg.employeeId = id;
		ServiceResult result = serviceHandler.doService(cert, new AddVacationCorrectionService(), arg);
		return ResponseUtil.toResponse(result);
	}

	@POST
	@Path("{id}/register")
	@Produces(MediaType.APPLICATION_JSON)
	public Response initiateRegistration(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new InitiateEmployeeRegistrationService(),
				new StringArgument(id));
		return ResponseUtil.toResponse(result);
	}
}
