package ch.eitchnet.chronivaro.rest.resource;

import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.WorkingLocationDurationType;
import ch.eitchnet.chronivaro.core.service.*;
import ch.eitchnet.chronivaro.rest.dto.*;
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
import li.strolch.service.StringResult;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.rest.dto.ChronivaroMapper.employeeToDto;
import static li.strolch.utils.helper.StringHelper.isNotEmpty;

@Path("chronivaro/v1/admin/employees")
public class EmployeeResource {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEmployees(@Context HttpServletRequest request, @QueryParam("offset") Integer offset,
			@QueryParam("limit") Integer limit) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<Resource> employees = tx.streamResources(TYPE_EMPLOYEE).toList();
			return PaginationHelper.toPagedOrListResponse(employees, offset, limit, e -> employeeToDto(tx, e));
		}
	}

	@GET
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEmployee(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, id, true);
			return ConcurrencyHelper.toResponseWithETag(employee, employeeToDto(tx, employee));
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
			return ChronivaroRestHelper.toResponse(result);

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, result.getValue(), true);
			return ConcurrencyHelper.toResponseWithETag(employee, employeeToDto(tx, employee));
		}
	}

	@PUT
	@Path("{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateEmployee(@Context HttpServletRequest request, @PathParam("id") String id, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, id, true);
			ConcurrencyHelper.validateIfMatch(request, employee);
		}

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
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, id, true);
				return ConcurrencyHelper.toResponseWithETag(employee, employeeToDto(tx, employee));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@DELETE
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response removeEmployee(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, id, true);
			ConcurrencyHelper.validateIfMatch(request, employee);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new RemoveEmployeeService(), new StringArgument(id));
		return ChronivaroRestHelper.toResponse(result);
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
		return ChronivaroRestHelper.toResponse(result);
	}

	@GET
	@Path("{id}/schedules")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getSchedules(@Context HttpServletRequest request, @PathParam("id") String id,
			@QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<Resource> schedules = tx
					.streamResources(TYPE_EMPLOYMENT_SCHEDULE)
					.filter(s -> s.getRelationId(PARAM_EMPLOYEE).equals(id))
					.toList();
			return PaginationHelper.toPagedOrListResponse(schedules, offset, limit, ChronivaroMapper::scheduleToDto);
		}
	}

	@GET
	@Path("{id}/schedules/{scheduleId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getSchedule(@Context HttpServletRequest request, @PathParam("id") String id,
			@PathParam("scheduleId") String scheduleId) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource schedule = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE, scheduleId, true);
			return ConcurrencyHelper.toResponseWithETag(schedule, ChronivaroMapper.scheduleToDto(schedule));
		}
	}

	@PUT
	@Path("{id}/schedules/{scheduleId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateSchedule(@Context HttpServletRequest request, @PathParam("id") String id,
			@PathParam("scheduleId") String scheduleId, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource schedule = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE, scheduleId, true);
			ConcurrencyHelper.validateIfMatch(request, schedule);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		UpdateScheduleService.UpdateScheduleArgument arg = ChronivaroRestHelper
				.createGson()
				.fromJson(data, UpdateScheduleService.UpdateScheduleArgument.class);
		arg.id = scheduleId;
		ServiceResult result = serviceHandler.doService(cert, new UpdateScheduleService(), arg);
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource schedule = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE, scheduleId, true);
				return ConcurrencyHelper.toResponseWithETag(schedule, ChronivaroMapper.scheduleToDto(schedule));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@DELETE
	@Path("{id}/schedules/{scheduleId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response removeSchedule(@Context HttpServletRequest request, @PathParam("id") String id,
			@PathParam("scheduleId") String scheduleId) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource schedule = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE, scheduleId, true);
			ConcurrencyHelper.validateIfMatch(request, schedule);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new RemoveScheduleService(),
				new StringArgument(scheduleId));
		return ChronivaroRestHelper.toResponse(result);
	}

	@GET
	@Path("{id}/working-location-defaults")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getWorkingLocationDefaults(@Context HttpServletRequest request, @PathParam("id") String id,
			@QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<WorkingLocationDefaultDto> defaults = tx
					.streamResources(TYPE_WORKING_LOCATION_DEFAULT)
					.filter(r -> id.equals(r.getRelationId(PARAM_EMPLOYEE)))
					.map(r -> new WorkingLocationDefaultDto(r.getId(), id,
							DayOfWeek.valueOf(r.getString(PARAM_WEEKDAY)),
							WorkingLocationDurationType.valueOf(r.getString(PARAM_DURATION_TYPE)),
							r.getString(PARAM_DAY_PART), r.getString(PARAM_WORKING_LOCATION)))
					.toList();
			return PaginationHelper.toPagedOrListResponse(defaults, offset, limit, Function.identity());
		}
	}

	@POST
	@Path("{id}/working-location-defaults")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createWorkingLocationDefault(@Context HttpServletRequest request, @PathParam("id") String id,
			String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		AddOrUpdateWorkingLocationDefaultService.Argument arg = ChronivaroRestHelper
				.createGson()
				.fromJson(data, AddOrUpdateWorkingLocationDefaultService.Argument.class);
		arg.employeeId = id;
		return ChronivaroRestHelper.toResponse(ChronivaroRestHelper
				.getServiceHandler()
				.doService(cert, new AddOrUpdateWorkingLocationDefaultService(), arg));
	}

	@PUT
	@Path("{id}/working-location-defaults/{defaultId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateWorkingLocationDefault(@Context HttpServletRequest request, @PathParam("id") String id,
			@PathParam("defaultId") String defaultId, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		AddOrUpdateWorkingLocationDefaultService.Argument arg = ChronivaroRestHelper
				.createGson()
				.fromJson(data, AddOrUpdateWorkingLocationDefaultService.Argument.class);
		arg.id = defaultId;
		arg.employeeId = id;
		return ChronivaroRestHelper.toResponse(ChronivaroRestHelper
				.getServiceHandler()
				.doService(cert, new AddOrUpdateWorkingLocationDefaultService(), arg));
	}

	@DELETE
	@Path("{id}/working-location-defaults/{defaultId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response removeWorkingLocationDefault(@Context HttpServletRequest request,
			@PathParam("defaultId") String defaultId) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		return ChronivaroRestHelper.toResponse(ChronivaroRestHelper
				.getServiceHandler()
				.doService(cert, new RemoveWorkingLocationDefaultService(), new StringArgument(defaultId)));
	}

	@GET
	@Path("{id}/vacation-account")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getVacationAccount(@Context HttpServletRequest request, @PathParam("id") String id,
			@QueryParam("year") Integer year, @QueryParam("summary") Boolean summary,
			@QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			tx.getResourceBy(TYPE_EMPLOYEE, id, true);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		GetVacationAccountSummaryService.GetVacationAccountSummaryArgument arg =
				new GetVacationAccountSummaryService.GetVacationAccountSummaryArgument(id, year);
		GetVacationAccountSummaryService.GetVacationAccountSummaryResult result =
				serviceHandler.doService(cert, new GetVacationAccountSummaryService(), arg);
		if (result.isOk()) {
			if (Boolean.TRUE.equals(summary)) {
				try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
					VacationAccountSummaryDto dto = ChronivaroMapper.vacationSummaryToDto(tx, result.summary, result.entries);
					return Response.ok(ChronivaroRestHelper.createGson().toJson(dto), MediaType.APPLICATION_JSON).build();
				}
			}
			return PaginationHelper.toPagedOrListResponse(result.entries, offset, limit, ChronivaroMapper::vacationEntryToDto);
		}
		return ChronivaroRestHelper.toResponse(result);
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
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("{id}/vacation-adjustments")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response addVacationAdjustment(@Context HttpServletRequest request, @PathParam("id") String id,
			String data) {
		return addVacationCorrection(request, id, data);
	}

	@POST
	@Path("{id}/vacation-entitlement/calculate")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response calculateVacationEntitlement(@Context HttpServletRequest request, @PathParam("id") String id,
			String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		CalculateVacationEntitlementService.CalculateVacationEntitlementArgument arg = isNotEmpty(data)
				? ChronivaroRestHelper.createGson().fromJson(data,
						CalculateVacationEntitlementService.CalculateVacationEntitlementArgument.class)
				: new CalculateVacationEntitlementService.CalculateVacationEntitlementArgument();
		arg.employeeId = id;
		if (arg.year == null) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, id, true);
				arg.year = LocalDate.now(ChronivaroModelHelper.getEmployeeTimezone(employee)).getYear();
			}
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		CalculateVacationEntitlementService.CalculateVacationEntitlementResult result =
				serviceHandler.doService(cert, new CalculateVacationEntitlementService(), arg);
		if (result.isOk()) {
			VacationEntitlementCalculationDto dto = new VacationEntitlementCalculationDto(id, arg.year,
					result.entitlementMinutes,
					result.summary != null ? ChronivaroMapper.vacationSummaryToDto(result.summary, null) : null);
			return Response.ok(ChronivaroRestHelper.createGson().toJson(dto), MediaType.APPLICATION_JSON).build();
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("{id}/vacation-entitlement/credit")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response creditVacationEntitlement(@Context HttpServletRequest request, @PathParam("id") String id,
			String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		CreditVacationEntitlementService.CreditVacationEntitlementArgument arg = isNotEmpty(data)
				? ChronivaroRestHelper.createGson().fromJson(data,
						CreditVacationEntitlementService.CreditVacationEntitlementArgument.class)
				: new CreditVacationEntitlementService.CreditVacationEntitlementArgument();
		arg.employeeId = id;
		if (arg.year == null) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, id, true);
				arg.year = LocalDate.now(ChronivaroModelHelper.getEmployeeTimezone(employee)).getYear();
			}
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		CreditVacationEntitlementService.CreditVacationEntitlementResult result =
				serviceHandler.doService(cert, new CreditVacationEntitlementService(), arg);
		if (result.isOk()) {
			VacationEntitlementCreditDto dto = new VacationEntitlementCreditDto(id, arg.year,
					result.entitlementMinutes, result.entryId);
			return Response.ok(ChronivaroRestHelper.createGson().toJson(dto), MediaType.APPLICATION_JSON).build();
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("{id}/reactivate")
	@Produces(MediaType.APPLICATION_JSON)
	public Response reactivateEmployee(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new ReactivateEmployeeService(),
				new StringArgument(id));
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("{id}/register")
	@Produces(MediaType.APPLICATION_JSON)
	public Response initiateRegistration(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new InitiateEmployeeRegistrationService(),
				new StringArgument(id));
		return ChronivaroRestHelper.toResponse(result);
	}
}
