package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.PeriodHelper;
import ch.atexxi.chronivaro.core.service.ApprovePeriodService;
import ch.atexxi.chronivaro.core.service.PeriodActionArgument;
import ch.atexxi.chronivaro.core.service.RejectPeriodService;
import ch.atexxi.chronivaro.core.service.ReopenPeriodService;
import ch.atexxi.chronivaro.core.service.SubmitPeriodService;
import ch.atexxi.chronivaro.rest.dto.ChronivaroMapper;
import ch.atexxi.chronivaro.rest.dto.PeriodActionRequestDto;
import ch.atexxi.chronivaro.rest.dto.PeriodStatusDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.StrolchRestfulConstants;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

import java.time.YearMonth;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.TYPE_TIME_PERIOD;
import static li.strolch.utils.helper.StringHelper.isEmpty;
import static li.strolch.utils.helper.StringHelper.isNotEmpty;

@Path("chronivaro/v1/periods")
public class PeriodResource {

	@GET
	@Path("status")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getPeriodStatus(
			@Context HttpServletRequest request,
			@QueryParam("yearMonth") String yearMonthStr,
			@QueryParam("employeeId") String employeeId) {

		if (isEmpty(yearMonthStr)) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "BAD_REQUEST",
					"Parameter yearMonth is required");
		}

		YearMonth yearMonth;
		try {
			yearMonth = YearMonth.parse(yearMonthStr);
		} catch (Exception e) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "BAD_REQUEST",
					"Invalid yearMonth format: " + yearMonthStr);
		}

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			String targetEmployeeId = employeeId;
			if (isEmpty(targetEmployeeId)) {
				Optional<Resource> currentEmployee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
				if (currentEmployee.isEmpty()) {
					return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
							"Employee not found for user: " + cert.getUsername());
				}
				targetEmployeeId = currentEmployee.get().getId();
			}

			Optional<Resource> period = PeriodHelper.findPeriod(tx, targetEmployeeId, yearMonth);
			if (period.isPresent()) {
				return ConcurrencyHelper.toResponseWithETag(period.get(), ChronivaroMapper.periodToDto(tx, period.get()));
			}
			PeriodStatusDto openDto = ChronivaroMapper.createOpenPeriodDto(tx, targetEmployeeId, yearMonth);
			return Response.ok(ChronivaroRestHelper.createGson().toJson(openDto), MediaType.APPLICATION_JSON).build();
		}
	}

	@POST
	@Path("submit")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response submitPeriod(@Context HttpServletRequest request, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		PeriodActionRequestDto dto = isNotEmpty(data) ?
				ChronivaroRestHelper.createGson().fromJson(data, PeriodActionRequestDto.class) : null;

		if (dto == null || isEmpty(dto.yearMonth())) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "BAD_REQUEST",
					"Field yearMonth is required in request body");
		}

		YearMonth yearMonth;
		try {
			yearMonth = YearMonth.parse(dto.yearMonth());
		} catch (Exception e) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "BAD_REQUEST",
					"Invalid yearMonth format: " + dto.yearMonth());
		}

		String employeeId = dto.employeeId();
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			if (isEmpty(employeeId)) {
				Optional<Resource> currentEmployee = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
				if (currentEmployee.isEmpty()) {
					return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
							"Employee not found for current user");
				}
				employeeId = currentEmployee.get().getId();
			}

			Optional<Resource> existing = PeriodHelper.findPeriod(tx, employeeId, yearMonth);
			existing.ifPresent(period -> ConcurrencyHelper.validateIfMatch(request, period));
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new SubmitPeriodService(),
				new PeriodActionArgument(employeeId, yearMonth, dto.comment()));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource period = PeriodHelper.getPeriod(tx, employeeId, yearMonth, true);
				return ConcurrencyHelper.toResponseWithETag(period, ChronivaroMapper.periodToDto(tx, period));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("approve")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response approvePeriod(@Context HttpServletRequest request, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		PeriodActionRequestDto dto = isNotEmpty(data) ?
				ChronivaroRestHelper.createGson().fromJson(data, PeriodActionRequestDto.class) : null;

		if (dto == null || isEmpty(dto.employeeId()) || isEmpty(dto.yearMonth())) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "BAD_REQUEST",
					"Fields employeeId and yearMonth are required in request body");
		}

		YearMonth yearMonth;
		try {
			yearMonth = YearMonth.parse(dto.yearMonth());
		} catch (Exception e) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "BAD_REQUEST",
					"Invalid yearMonth format: " + dto.yearMonth());
		}

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> existing = PeriodHelper.findPeriod(tx, dto.employeeId(), yearMonth);
			existing.ifPresent(period -> ConcurrencyHelper.validateIfMatch(request, period));
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new ApprovePeriodService(),
				new PeriodActionArgument(dto.employeeId(), yearMonth, dto.comment()));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource period = PeriodHelper.getPeriod(tx, dto.employeeId(), yearMonth, true);
				return ConcurrencyHelper.toResponseWithETag(period, ChronivaroMapper.periodToDto(tx, period));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("reject")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response rejectPeriod(@Context HttpServletRequest request, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		PeriodActionRequestDto dto = isNotEmpty(data) ?
				ChronivaroRestHelper.createGson().fromJson(data, PeriodActionRequestDto.class) : null;

		if (dto == null || isEmpty(dto.employeeId()) || isEmpty(dto.yearMonth())) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "BAD_REQUEST",
					"Fields employeeId and yearMonth are required in request body");
		}
		if (isEmpty(dto.comment())) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "VALIDATION_ERROR",
					"Rejection reason must be provided in comment");
		}

		YearMonth yearMonth;
		try {
			yearMonth = YearMonth.parse(dto.yearMonth());
		} catch (Exception e) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "BAD_REQUEST",
					"Invalid yearMonth format: " + dto.yearMonth());
		}

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> existing = PeriodHelper.findPeriod(tx, dto.employeeId(), yearMonth);
			existing.ifPresent(period -> ConcurrencyHelper.validateIfMatch(request, period));
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new RejectPeriodService(),
				new PeriodActionArgument(dto.employeeId(), yearMonth, dto.comment()));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource period = PeriodHelper.getPeriod(tx, dto.employeeId(), yearMonth, true);
				return ConcurrencyHelper.toResponseWithETag(period, ChronivaroMapper.periodToDto(tx, period));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("reopen")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response reopenPeriod(@Context HttpServletRequest request, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		PeriodActionRequestDto dto = isNotEmpty(data) ?
				ChronivaroRestHelper.createGson().fromJson(data, PeriodActionRequestDto.class) : null;

		if (dto == null || isEmpty(dto.employeeId()) || isEmpty(dto.yearMonth())) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "BAD_REQUEST",
					"Fields employeeId and yearMonth are required in request body");
		}
		if (isEmpty(dto.comment())) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "VALIDATION_ERROR",
					"Reopen reason must be provided in comment");
		}

		YearMonth yearMonth;
		try {
			yearMonth = YearMonth.parse(dto.yearMonth());
		} catch (Exception e) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "BAD_REQUEST",
					"Invalid yearMonth format: " + dto.yearMonth());
		}

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Optional<Resource> existing = PeriodHelper.findPeriod(tx, dto.employeeId(), yearMonth);
			existing.ifPresent(period -> ConcurrencyHelper.validateIfMatch(request, period));
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new ReopenPeriodService(),
				new PeriodActionArgument(dto.employeeId(), yearMonth, dto.comment()));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource period = PeriodHelper.getPeriod(tx, dto.employeeId(), yearMonth, true);
				return ConcurrencyHelper.toResponseWithETag(period, ChronivaroMapper.periodToDto(tx, period));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("{id}/reopen")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response reopenPeriodById(
			@Context HttpServletRequest request,
			@PathParam("id") String id,
			String data) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		PeriodActionRequestDto dto = isNotEmpty(data) ?
				ChronivaroRestHelper.createGson().fromJson(data, PeriodActionRequestDto.class) : null;

		String comment = dto != null ? dto.comment() : null;
		if (isEmpty(comment)) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "VALIDATION_ERROR",
					"Reopen reason must be provided in comment");
		}

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, id, true);
			ConcurrencyHelper.validateIfMatch(request, period);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new ReopenPeriodService(),
				new PeriodActionArgument(id, comment));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, id, true);
				return ConcurrencyHelper.toResponseWithETag(period, ChronivaroMapper.periodToDto(tx, period));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}
}
