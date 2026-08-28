package ch.eitchnet.chronivaro.rest.resource;

import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.PeriodHelper;
import ch.eitchnet.chronivaro.core.model.WorkEntryHelper;
import ch.eitchnet.chronivaro.core.model.WorkingLocation;
import ch.eitchnet.chronivaro.core.model.WorkingLocationDurationType;
import ch.eitchnet.chronivaro.core.service.*;
import ch.eitchnet.chronivaro.rest.dto.AbsenceDto;
import ch.eitchnet.chronivaro.rest.dto.ChronivaroMapper;
import ch.eitchnet.chronivaro.rest.dto.PeriodActionRequestDto;
import ch.eitchnet.chronivaro.rest.dto.VacationAccountSummaryDto;
import ch.eitchnet.chronivaro.rest.dto.WorkEntryDto;
import ch.eitchnet.chronivaro.rest.dto.WorkingLocationDefaultDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.agent.api.StrolchAgent;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.base.AccessDeniedException;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.RestfulStrolchComponent;
import li.strolch.service.StringArgument;
import li.strolch.service.StringResult;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.rest.StrolchRestfulConstants.STROLCH_CERTIFICATE;
import static li.strolch.rest.StrolchRestfulConstants.STROLCH_REMOTE_IP;
import static li.strolch.utils.helper.StringHelper.isNotEmpty;

@Path("chronivaro/v1")
public class ChronivaroResource {

	@GET
	@Path("presence")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getPresence(@Context HttpServletRequest request, @QueryParam("teamId") String teamId,
			@QueryParam("locationId") String locationId, @QueryParam("offset") Integer offset,
			@QueryParam("limit") Integer limit) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();

		PresenceService.PresenceArgument arg = new PresenceService.PresenceArgument();
		arg.teamId = teamId;
		arg.locationId = locationId;

		PresenceService.PresenceResult result = serviceHandler.doService(cert, new PresenceService(), arg);
		if (result.isOk()) {
			return PaginationHelper.toPagedOrListResponse(result.presenceInfos, offset, limit,
					ChronivaroMapper::toDto);
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@GET
	@Path("me/profile")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMyProfile(@Context HttpServletRequest request) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty()) {
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
						"Employee profile not found for current user");
			}
			Resource empRes = employee.get();
			return ConcurrencyHelper.toResponseWithETag(empRes, ChronivaroMapper.employeeToDto(tx, empRes));
		}
	}

	@GET
	@Path("me/schedules")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMySchedules(@Context HttpServletRequest request, @QueryParam("offset") Integer offset,
			@QueryParam("limit") Integer limit) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty()) {
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
						"Employee profile not found for current user");
			}
			String employeeId = employee.get().getId();
			List<Resource> schedules = tx
					.streamResources(TYPE_EMPLOYMENT_SCHEDULE)
					.filter(s -> s.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.toList();
			return PaginationHelper.toPagedOrListResponse(schedules, offset, limit, ChronivaroMapper::scheduleToDto);
		}
	}

	@GET
	@Path("me/work-entries")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMyWorkEntries(@Context HttpServletRequest request, @QueryParam("from") String fromStr,
			@QueryParam("to") String toStr, @QueryParam("offset") Integer offset,
			@QueryParam("limit") Integer limit) {

		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
						"Employee not found for current user");
			employeeId = employee.get().getId();
		}

		ZonedDateTime from = null;
		if (isNotEmpty(fromStr)) {
			try {
				from = fromStr.contains("T") ? ZonedDateTime.parse(fromStr) : LocalDate.parse(fromStr).atStartOfDay(ZoneId.of("UTC"));
			} catch (Exception e) {
				return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "INVALID_PARAMETER",
						"Invalid 'from' date format: " + fromStr);
			}
		}

		ZonedDateTime to = null;
		if (isNotEmpty(toStr)) {
			try {
				to = toStr.contains("T") ? ZonedDateTime.parse(toStr) : LocalDate.parse(toStr).atTime(23, 59, 59, 999999999).atZone(ZoneId.of("UTC"));
			} catch (Exception e) {
				return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "INVALID_PARAMETER",
						"Invalid 'to' date format: " + toStr);
			}
		}

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<Resource> entries = WorkEntryHelper.findWorkEntries(tx, employeeId, from, to);
			return PaginationHelper.toPagedOrListResponse(entries, offset, limit, ChronivaroMapper::toDto);
		}
	}

	@GET
	@Path("me/working-location-defaults")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMyWorkingLocationDefaults(@Context HttpServletRequest request,
			@QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return Response.status(Response.Status.NOT_FOUND).build();
			String employeeId = employee.get().getId();
			List<WorkingLocationDefaultDto> defaults = tx.streamResources(TYPE_WORKING_LOCATION_DEFAULT)
					.filter(r -> employeeId.equals(r.getRelationId(PARAM_EMPLOYEE)))
					.map(r -> new WorkingLocationDefaultDto(r.getId(), employeeId,
							DayOfWeek.valueOf(r.getString(PARAM_WEEKDAY)),
							WorkingLocationDurationType.valueOf(r.getString(PARAM_DURATION_TYPE)),
							r.getString(PARAM_DAY_PART), r.getString(PARAM_WORKING_LOCATION)))
					.toList();
			return PaginationHelper.toPagedOrListResponse(defaults, offset, limit, java.util.function.Function.identity());
		}
	}

	@GET
	@Path("me/work-entries/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMyWorkEntry(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource workEntry = tx.getResourceBy(TYPE_WORK_ENTRY, id, true);
			return ConcurrencyHelper.toResponseWithETag(workEntry, ChronivaroMapper.toDto(workEntry));
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

		if (dto.start() != null && dto.end() != null && (dto.end().isBefore(dto.start()) || dto.end().isEqual(dto.start()))) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "INVALID_ENTRY_DURATION",
					"Work entry end time must be strictly after start time");
		}

		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
						"Employee not found for current user");
			employeeId = employee.get().getId();
		}

		AddWorkEntryService.AddWorkEntryArgument arg = new AddWorkEntryService.AddWorkEntryArgument();
		arg.employeeId = employeeId;
		arg.start = dto.start();
		arg.end = dto.end();
		arg.comment = dto.comment();
		arg.workingLocation = dto.workingLocation();

		ServiceResult result = serviceHandler.doService(cert, new AddWorkEntryService(), arg);
		if (result.isOk() && result instanceof StringResult stringResult) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource workEntry = tx.getResourceBy(TYPE_WORK_ENTRY, stringResult.getValue(), true);
				return ConcurrencyHelper.toResponseWithETag(workEntry, ChronivaroMapper.toDto(workEntry));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@PUT
	@Path("me/work-entries/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateWorkEntry(@Context HttpServletRequest request, @PathParam("id") String id, String data) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource workEntry = tx.getResourceBy(TYPE_WORK_ENTRY, id, true);
			ConcurrencyHelper.validateIfMatch(request, workEntry);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		WorkEntryDto dto = ChronivaroRestHelper.createGson().fromJson(data, WorkEntryDto.class);

		if (dto.start() != null && dto.end() != null && (dto.end().isBefore(dto.start()) || dto.end().isEqual(dto.start()))) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "INVALID_ENTRY_DURATION",
					"Work entry end time must be strictly after start time");
		}

		CorrectWorkEntryService.CorrectWorkEntryArgument arg = new CorrectWorkEntryService.CorrectWorkEntryArgument();
		arg.workEntryId = id;
		arg.start = dto.start();
		arg.end = dto.end();
		arg.comment = dto.comment();
		arg.workingLocation = dto.workingLocation();

		ServiceResult result = serviceHandler.doService(cert, new CorrectWorkEntryService(), arg);
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource workEntry = tx.getResourceBy(TYPE_WORK_ENTRY, id, true);
				return ConcurrencyHelper.toResponseWithETag(workEntry, ChronivaroMapper.toDto(workEntry));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@PUT
	@Path("admin/work-entries/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response adminUpdateWorkEntry(@Context HttpServletRequest request, @PathParam("id") String id, String data) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource workEntry = tx.getResourceBy(TYPE_WORK_ENTRY, id, true);
			ConcurrencyHelper.validateIfMatch(request, workEntry);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		WorkEntryDto dto = ChronivaroRestHelper.createGson().fromJson(data, WorkEntryDto.class);

		if (dto.start() != null && dto.end() != null && (dto.end().isBefore(dto.start()) || dto.end().isEqual(dto.start()))) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "INVALID_ENTRY_DURATION",
					"Work entry end time must be strictly after start time");
		}

		CorrectWorkEntryService.CorrectWorkEntryArgument arg = new CorrectWorkEntryService.CorrectWorkEntryArgument();
		arg.workEntryId = id;
		arg.start = dto.start();
		arg.end = dto.end();
		arg.comment = dto.comment();
		arg.workingLocation = dto.workingLocation();

		ServiceResult result = serviceHandler.doService(cert, new CorrectWorkEntryService(), arg);
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource workEntry = tx.getResourceBy(TYPE_WORK_ENTRY, id, true);
				return ConcurrencyHelper.toResponseWithETag(workEntry, ChronivaroMapper.toDto(workEntry));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@DELETE
	@Path("admin/work-entries/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response adminDeleteWorkEntry(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new RemoveWorkEntryService(), new StringArgument(id));
		return ChronivaroRestHelper.toResponse(result);
	}

	@GET
	@Path("employees/{id}/work-entries")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getEmployeeWorkEntries(@Context HttpServletRequest request, @PathParam("id") String employeeId,
			@QueryParam("from") String fromStr, @QueryParam("to") String toStr,
			@QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit) {

		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);

		ZonedDateTime from = null;
		if (isNotEmpty(fromStr)) {
			try {
				from = fromStr.contains("T") ? ZonedDateTime.parse(fromStr) : LocalDate.parse(fromStr).atStartOfDay(ZoneId.of("UTC"));
			} catch (Exception e) {
				return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "INVALID_PARAMETER",
						"Invalid 'from' date format: " + fromStr);
			}
		}

		ZonedDateTime to = null;
		if (isNotEmpty(toStr)) {
			try {
				to = toStr.contains("T") ? ZonedDateTime.parse(toStr) : LocalDate.parse(toStr).atTime(23, 59, 59, 999999999).atZone(ZoneId.of("UTC"));
			} catch (Exception e) {
				return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "INVALID_PARAMETER",
						"Invalid 'to' date format: " + toStr);
			}
		}

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			assertCanAccessEmployeeWorkEntries(tx, cert, employeeId);
			List<Resource> entries = WorkEntryHelper.findWorkEntries(tx, employeeId, from, to);
			return PaginationHelper.toPagedOrListResponse(entries, offset, limit, ChronivaroMapper::toDto);
		}
	}

	@POST
	@Path("employees/{id}/work-entries")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createEmployeeWorkEntry(@Context HttpServletRequest request, @PathParam("id") String employeeId,
			String data) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		WorkEntryDto dto = ChronivaroRestHelper.createGson().fromJson(data, WorkEntryDto.class);

		if (dto.start() != null && dto.end() != null && (dto.end().isBefore(dto.start()) || dto.end().isEqual(dto.start()))) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "INVALID_ENTRY_DURATION",
					"Work entry end time must be strictly after start time");
		}

		AddWorkEntryService.AddWorkEntryArgument arg = new AddWorkEntryService.AddWorkEntryArgument();
		arg.employeeId = employeeId;
		arg.start = dto.start();
		arg.end = dto.end();
		arg.comment = dto.comment();
		arg.workingLocation = dto.workingLocation();

		ServiceResult result = serviceHandler.doService(cert, new AddWorkEntryService(), arg);
		if (result.isOk() && result instanceof StringResult stringResult) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource workEntry = tx.getResourceBy(TYPE_WORK_ENTRY, stringResult.getValue(), true);
				return ConcurrencyHelper.toResponseWithETag(workEntry, ChronivaroMapper.toDto(workEntry));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	private void assertCanAccessEmployeeWorkEntries(StrolchTransaction tx, Certificate cert, String employeeId) {
		if (tx.getPrivilegeContext().hasRole(ROLE_HR)
				|| tx.getPrivilegeContext().hasRole(ROLE_ADMIN)
				|| tx.getPrivilegeContext().hasRole(ROLE_ADMINISTRATOR)) {
			return;
		}

		Optional<Resource> callerEmp = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
		if (callerEmp.isPresent() && callerEmp.get().getId().equals(employeeId)) {
			return;
		}

		if (tx.getPrivilegeContext().hasRole(ROLE_SUPERVISOR)) {
			List<String> supervised = ChronivaroModelHelper.getSupervisedEmployeeIds(tx, cert);
			if (supervised.contains(employeeId)) {
				return;
			}
		}

		throw new AccessDeniedException("You do not have permission to access work entries for employee " + employeeId);
	}

	@GET
	@Path("me/vacation-account")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMyVacationAccount(@Context HttpServletRequest request, @QueryParam("year") Integer year) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
						"Employee not found for current user");
			employeeId = employee.get().getId();
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		GetVacationAccountSummaryService.GetVacationAccountSummaryArgument arg =
				new GetVacationAccountSummaryService.GetVacationAccountSummaryArgument(employeeId, year);
		GetVacationAccountSummaryService.GetVacationAccountSummaryResult result =
				serviceHandler.doService(cert, new GetVacationAccountSummaryService(), arg);
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				VacationAccountSummaryDto dto = ChronivaroMapper.vacationSummaryToDto(tx, result.summary, result.entries);
				return Response.ok(ChronivaroRestHelper.createGson().toJson(dto), MediaType.APPLICATION_JSON).build();
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@GET
	@Path("me/absences")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMyAbsences(@Context HttpServletRequest request,
			@QueryParam("from") String fromStr,
			@QueryParam("to") String toStr,
			@QueryParam("absenceTypeCode") String absenceTypeCode,
			@QueryParam("status") String status,
			@QueryParam("offset") Integer offset,
			@QueryParam("limit") Integer limit) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
						"Employee not found for current user");
			employeeId = employee.get().getId();
		}

		LocalDate fromDate = null;
		if (isNotEmpty(fromStr)) {
			try {
				fromDate = fromStr.contains("T") ? ZonedDateTime.parse(fromStr).toLocalDate() : LocalDate.parse(fromStr);
			} catch (Exception e) {
				return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "INVALID_PARAMETER",
						"Invalid 'from' date format: " + fromStr);
			}
		}

		LocalDate toDate = null;
		if (isNotEmpty(toStr)) {
			try {
				toDate = toStr.contains("T") ? ZonedDateTime.parse(toStr).toLocalDate() : LocalDate.parse(toStr);
			} catch (Exception e) {
				return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "INVALID_PARAMETER",
						"Invalid 'to' date format: " + toStr);
			}
		}

		final LocalDate fFrom = fromDate;
		final LocalDate fTo = toDate;

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<Resource> absences = tx
					.streamResources(TYPE_ABSENCE)
					.filter(a -> a.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.filter(a -> {
						if (fFrom != null && a.getDate(PARAM_END).toLocalDate().isBefore(fFrom))
							return false;
						if (fTo != null && a.getDate(PARAM_START).toLocalDate().isAfter(fTo))
							return false;
						if (isNotEmpty(status) && !a.getString(PARAM_STATE).equalsIgnoreCase(status))
							return false;
						if (isNotEmpty(absenceTypeCode)) {
							Resource type = tx.getResourceByRelation(a, PARAM_ABSENCE_TYPE, false);
							if (type == null || !type.getString(PARAM_CODE).equalsIgnoreCase(absenceTypeCode))
								return false;
						}
						return true;
					})
					.sorted(java.util.Comparator.comparing(a -> a.getDate(PARAM_START)))
					.toList();
			return PaginationHelper.toPagedOrListResponse(absences, offset, limit, a -> {
				Resource type = tx.getResourceByRelation(a, PARAM_ABSENCE_TYPE, true);
				return ChronivaroMapper.toDto(tx, a, type);
			});
		}
	}

	@GET
	@Path("me/absences/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMyAbsence(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
						"Employee not found for current user");
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
			if (!absence.getRelationId(PARAM_EMPLOYEE).equals(employee.get().getId())) {
				return ChronivaroRestHelper.toErrorResponse(Response.Status.FORBIDDEN, "ACCESS_DENIED",
						"Access denied to absence " + id);
			}
			Resource type = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
			return ConcurrencyHelper.toResponseWithETag(absence, ChronivaroMapper.toDto(tx, absence, type));
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
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
						"Employee not found for current user");
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
		arg.state = dto.state();

		StringResult result = serviceHandler.doService(cert, new RequestAbsenceService(), arg);
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource absence = tx.getResourceBy(TYPE_ABSENCE, result.getValue(), true);
				Resource type = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
				return ConcurrencyHelper.toResponseWithETag(absence, ChronivaroMapper.toDto(tx, absence, type));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@PUT
	@Path("me/absences/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateAbsence(@Context HttpServletRequest request, @PathParam("id") String id, String data) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
						"Employee not found for current user");
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
			if (!absence.getRelationId(PARAM_EMPLOYEE).equals(employee.get().getId())) {
				return ChronivaroRestHelper.toErrorResponse(Response.Status.FORBIDDEN, "ACCESS_DENIED",
						"Access denied to absence " + id);
			}
			ConcurrencyHelper.validateIfMatch(request, absence);
		}

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
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
				Resource type = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
				return ConcurrencyHelper.toResponseWithETag(absence, ChronivaroMapper.toDto(tx, absence, type));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("me/absences/{id}/submit")
	@Produces(MediaType.APPLICATION_JSON)
	public Response submitAbsence(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
						"Employee not found for current user");
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
			if (!absence.getRelationId(PARAM_EMPLOYEE).equals(employee.get().getId())) {
				return ChronivaroRestHelper.toErrorResponse(Response.Status.FORBIDDEN, "ACCESS_DENIED",
						"Access denied to absence " + id);
			}
			ConcurrencyHelper.validateIfMatch(request, absence);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new SubmitAbsenceService(), new StringArgument(id));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
				Resource type = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
				return ConcurrencyHelper.toResponseWithETag(absence, ChronivaroMapper.toDto(tx, absence, type));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("me/absences/{id}/cancel")
	@Consumes({MediaType.APPLICATION_JSON, MediaType.WILDCARD})
	@Produces(MediaType.APPLICATION_JSON)
	public Response cancelAbsence(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
						"Employee not found for current user");
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
			if (!absence.getRelationId(PARAM_EMPLOYEE).equals(employee.get().getId())) {
				return ChronivaroRestHelper.toErrorResponse(Response.Status.FORBIDDEN, "ACCESS_DENIED",
						"Access denied to absence " + id);
			}
			ConcurrencyHelper.validateIfMatch(request, absence);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new CancelAbsenceService(), new StringArgument(id));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
				Resource type = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
				return ConcurrencyHelper.toResponseWithETag(absence, ChronivaroMapper.toDto(tx, absence, type));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("me/timer/start")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response startTimer(@Context HttpServletRequest request, String data) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();

		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
						"Employee not found for current user");
			employeeId = employee.get().getId();
		}

		TimerStartDto dataObject = ChronivaroRestHelper.createGson().fromJson(data, TimerStartDto.class);
		StartTimerService.Argument arg = new StartTimerService.Argument();
		arg.employeeId = employeeId;
		arg.workingLocation = WorkingLocation.valueOf(dataObject.workingLocation);
		ServiceResult result = serviceHandler.doService(cert, new StartTimerService(), arg);
		return ChronivaroRestHelper.toResponse(result);
	}

	private record TimerStartDto(String workingLocation) {
	}

	@POST
	@Path("me/timer/stop")
	@Consumes({MediaType.APPLICATION_JSON, MediaType.WILDCARD})
	@Produces(MediaType.APPLICATION_JSON)
	public Response stopTimer(@Context HttpServletRequest request, String data) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();

		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
						"Employee not found for current user");
			employeeId = employee.get().getId();
		}

		String comment = null;
		if (isNotEmpty(data)) {
			try {
				TimerStopDto stopDto = ChronivaroRestHelper.createGson().fromJson(data, TimerStopDto.class);
				if (stopDto != null) {
					comment = stopDto.comment();
				}
			} catch (Exception ignored) {
			}
		}

		StopTimerService.StopTimerArgument arg = new StopTimerService.StopTimerArgument(employeeId, comment);
		ServiceResult result = serviceHandler.doService(cert, new StopTimerService(), arg);
		return ChronivaroRestHelper.toResponse(result);
	}

	private record TimerStopDto(String comment) {
	}

	@GET
	@Path("me/periods/{yearMonth}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMyPeriodStatus(@Context HttpServletRequest request, @PathParam("yearMonth") String yearMonthStr) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		YearMonth ym;
		try {
			ym = YearMonth.parse(yearMonthStr);
		} catch (Exception e) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "BAD_REQUEST",
					"Invalid yearMonth format: " + yearMonthStr);
		}

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
						"Employee not found for current user");

			Optional<Resource> period = PeriodHelper.findPeriod(tx, employee.get().getId(), ym);
			if (period.isPresent()) {
				return ConcurrencyHelper.toResponseWithETag(period.get(), ChronivaroMapper.periodToDto(tx, period.get()));
			}
			ch.eitchnet.chronivaro.rest.dto.PeriodStatusDto openDto = ChronivaroMapper.createOpenPeriodDto(tx, employee.get().getId(), ym);
			return Response.ok(ChronivaroRestHelper.createGson().toJson(openDto), MediaType.APPLICATION_JSON).build();
		}
	}

	@POST
	@Path("me/periods/{yearMonth}/submit")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response submitMyPeriod(
			@Context HttpServletRequest request,
			@PathParam("yearMonth") String yearMonthStr,
			String data) {

		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		YearMonth ym;
		try {
			ym = YearMonth.parse(yearMonthStr);
		} catch (Exception e) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "BAD_REQUEST",
					"Invalid yearMonth format: " + yearMonthStr);
		}

		PeriodActionRequestDto dto = isNotEmpty(data) ?
				ChronivaroRestHelper.createGson().fromJson(data, PeriodActionRequestDto.class) : null;

		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
						"Employee not found for current user");
			employeeId = employee.get().getId();

			Optional<Resource> existing = PeriodHelper.findPeriod(tx, employeeId, ym);
			existing.ifPresent(period -> ConcurrencyHelper.validateIfMatch(request, period));
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		String comment = dto != null ? dto.comment() : null;
		ServiceResult result = serviceHandler.doService(cert, new SubmitPeriodService(),
				new PeriodActionArgument(employeeId, ym, comment));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource period = PeriodHelper.getPeriod(tx, employeeId, ym, true);
				return ConcurrencyHelper.toResponseWithETag(period, ChronivaroMapper.periodToDto(tx, period));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("me/periods/{id}/submit")
	@Produces(MediaType.APPLICATION_JSON)
	public Response submitPeriod(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, id, true);
			ConcurrencyHelper.validateIfMatch(request, period);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new SubmitPeriodService(), new PeriodActionArgument(id));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, id, true);
				return ConcurrencyHelper.toResponseWithETag(period, ChronivaroMapper.periodToDto(tx, period));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
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

		return ChronivaroRestHelper.toResponse(result);
	}

	@GET
	@Path("me/day-summary/{date}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getDaySummary(@Context HttpServletRequest request, @PathParam("date") String dateStr) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();

		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
						"Employee not found for current user");
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
		return ChronivaroRestHelper.toResponse(result);
	}

	@GET
	@Path("me/month-summary/{yearMonth}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMonthSummary(@Context HttpServletRequest request, @PathParam("yearMonth") String yearMonthStr) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();

		String employeeId;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> employee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
			if (employee.isEmpty())
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
						"Employee not found for current user");
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
		return ChronivaroRestHelper.toResponse(result);
	}
}
