package ch.eitchnet.chronivaro.rest.resource;

import ch.eitchnet.chronivaro.core.model.AbsenceHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.search.AbsenceSearch;
import ch.eitchnet.chronivaro.core.search.TimePeriodSearch;
import ch.eitchnet.chronivaro.core.service.ApproveAbsenceService;
import ch.eitchnet.chronivaro.core.service.ApprovePeriodService;
import ch.eitchnet.chronivaro.core.service.MonthSummaryService;
import ch.eitchnet.chronivaro.core.service.PeriodActionArgument;
import ch.eitchnet.chronivaro.core.service.RejectAbsenceService;
import ch.eitchnet.chronivaro.core.service.RejectPeriodService;
import ch.eitchnet.chronivaro.rest.dto.ChronivaroMapper;
import ch.eitchnet.chronivaro.rest.dto.PeriodActionRequestDto;
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
import li.strolch.search.SearchResult;
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.*;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.utils.helper.StringHelper.isEmpty;
import static li.strolch.utils.helper.StringHelper.isNotEmpty;

@Path("chronivaro/v1/approvals")
public class ApprovalsResource {

	@GET
	@Path("periods")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getSubmittedPeriods(
			@Context HttpServletRequest request,
			@QueryParam("teamId") String teamId,
			@QueryParam("employeeId") String employeeId,
			@QueryParam("yearMonth") String yearMonth,
			@QueryParam("offset") Integer offset,
			@QueryParam("limit") Integer limit) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<String> supervisedEmployeeIds = ChronivaroModelHelper.getSupervisedEmployeeIds(tx, cert);
			if (supervisedEmployeeIds.isEmpty()) {
				List<Resource> empty = List.of();
				return PaginationHelper.toPagedOrListResponse(empty, offset, limit, p -> ChronivaroMapper.periodToDto(tx, p));
			}

			Set<String> targetEmployeeIds = new HashSet<>(supervisedEmployeeIds);
			if (isNotEmpty(teamId)) {
				List<String> teamEmployeeIds = tx.streamResources(TYPE_EMPLOYEE)
						.filter(e -> teamId.equals(e.getRelationId(PARAM_PRIMARY_TEAM)))
						.map(Resource::getId)
						.toList();
				targetEmployeeIds.retainAll(teamEmployeeIds);
			}
			if (isNotEmpty(employeeId)) {
				targetEmployeeIds.retainAll(Set.of(employeeId));
			}
			if (targetEmployeeIds.isEmpty()) {
				List<Resource> empty = List.of();
				return PaginationHelper.toPagedOrListResponse(empty, offset, limit, p -> ChronivaroMapper.periodToDto(tx, p));
			}

			TimePeriodSearch search = new TimePeriodSearch()
					.forState(STATE_SUBMITTED)
					.forEmployees(targetEmployeeIds);
			if (isNotEmpty(yearMonth)) {
				search.forYearMonth(yearMonth);
			}

			SearchResult<Resource> searchResult = search.search(tx);
			return PaginationHelper.toPagedOrListResponse(searchResult, offset, limit, p -> ChronivaroMapper.periodToDto(tx, p));
		}
	}

	@GET
	@Path("periods/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getSubmittedPeriodDetail(
			@Context HttpServletRequest request,
			@PathParam("id") String id) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		String employeeId;
		YearMonth yearMonth;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, id, false);
			if (period == null) {
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND",
						"Time period " + id + " not found");
			}
			employeeId = period.getRelationId(PARAM_EMPLOYEE);
			if (isEmpty(employeeId)) {
				return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "BAD_REQUEST",
						"Time period " + id + " has no associated employee");
			}
			try {
				yearMonth = YearMonth.parse(period.getString(PARAM_YEAR_MONTH));
			} catch (Exception e) {
				return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "BAD_REQUEST",
						"Invalid yearMonth in period: " + period.getString(PARAM_YEAR_MONTH));
			}
			ChronivaroModelHelper.assertCanManageEmployee(tx, employeeId);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		MonthSummaryService.MonthSummaryArgument arg = new MonthSummaryService.MonthSummaryArgument();
		arg.employeeId = employeeId;
		arg.yearMonth = yearMonth;

		MonthSummaryService.MonthSummaryResult result = serviceHandler.doService(cert, new MonthSummaryService(), arg);
		if (!result.isOk()) {
			return ChronivaroRestHelper.toResponse(result);
		}

		return Response.ok(ChronivaroRestHelper.createGson().toJson(ChronivaroMapper.toDto(result.monthSummary)),
				MediaType.APPLICATION_JSON).build();
	}

	@POST
	@Path("periods/{id}/approve")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response approvePeriod(
			@Context HttpServletRequest request,
			@PathParam("id") String id,
			String data) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, id, true);
			ConcurrencyHelper.validateIfMatch(request, period);
		}

		PeriodActionRequestDto dto = isNotEmpty(data) ?
				ChronivaroRestHelper.createGson().fromJson(data, PeriodActionRequestDto.class) : null;

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		String comment = dto != null ? dto.comment() : null;
		ServiceResult result = serviceHandler.doService(cert, new ApprovePeriodService(),
				new PeriodActionArgument(id, comment));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, id, true);
				return ConcurrencyHelper.toResponseWithETag(period, ChronivaroMapper.periodToDto(tx, period));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@POST
	@Path("periods/{id}/reject")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response rejectPeriod(
			@Context HttpServletRequest request,
			@PathParam("id") String id,
			String data) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		PeriodActionRequestDto dto = isNotEmpty(data) ?
				ChronivaroRestHelper.createGson().fromJson(data, PeriodActionRequestDto.class) : null;

		String comment = dto != null ? dto.comment() : null;
		if (isEmpty(comment)) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "VALIDATION_ERROR",
					"Rejection reason must be provided in comment");
		}

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, id, true);
			ConcurrencyHelper.validateIfMatch(request, period);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new RejectPeriodService(),
				new PeriodActionArgument(id, comment));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource period = tx.getResourceBy(TYPE_TIME_PERIOD, id, true);
				return ConcurrencyHelper.toResponseWithETag(period, ChronivaroMapper.periodToDto(tx, period));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}

	@GET
	@Path("absences")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getSubmittedAbsences(
			@Context HttpServletRequest request,
			@QueryParam("teamId") String teamId,
			@QueryParam("employeeId") String employeeId,
			@QueryParam("absenceTypeCode") String absenceTypeCode,
			@QueryParam("from") String fromStr,
			@QueryParam("to") String toStr,
			@QueryParam("offset") Integer offset,
			@QueryParam("limit") Integer limit) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
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
			List<String> supervisedEmployeeIds = ChronivaroModelHelper.getSupervisedEmployeeIds(tx, cert);
			if (supervisedEmployeeIds.isEmpty()) {
				List<Resource> empty = List.of();
				return PaginationHelper.toPagedOrListResponse(empty, offset, limit, absence -> {
					Resource type = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
					return ChronivaroMapper.toDto(tx, absence, type);
				});
			}

			Set<String> targetEmployeeIds = new HashSet<>(supervisedEmployeeIds);
			if (isNotEmpty(teamId)) {
				List<String> teamEmployeeIds = tx.streamResources(TYPE_EMPLOYEE)
						.filter(e -> teamId.equals(e.getRelationId(PARAM_PRIMARY_TEAM)))
						.map(Resource::getId)
						.toList();
				targetEmployeeIds.retainAll(teamEmployeeIds);
			}
			if (isNotEmpty(employeeId)) {
				targetEmployeeIds.retainAll(Set.of(employeeId));
			}
			if (targetEmployeeIds.isEmpty()) {
				List<Resource> empty = List.of();
				return PaginationHelper.toPagedOrListResponse(empty, offset, limit, absence -> {
					Resource type = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
					return ChronivaroMapper.toDto(tx, absence, type);
				});
			}

			String absenceTypeId = null;
			if (isNotEmpty(absenceTypeCode)) {
				Resource type = AbsenceHelper.getAbsenceType(tx, absenceTypeCode);
				absenceTypeId = type.getId();
			}

			AbsenceSearch search = new AbsenceSearch()
					.forState(STATE_SUBMITTED)
					.forEmployees(targetEmployeeIds);
			if (isNotEmpty(absenceTypeId)) {
				search.forAbsenceType(absenceTypeId);
			}

			List<Resource> submittedAbsences = search.search(tx).toList().stream()
					.filter(a -> {
						if (fFrom != null && a.getDate(PARAM_END).toLocalDate().isBefore(fFrom))
							return false;
						if (fTo != null && a.getDate(PARAM_START).toLocalDate().isAfter(fTo))
							return false;
						return true;
					})
					.sorted(Comparator.comparing(a -> a.getDate(PARAM_START)))
					.toList();

			return PaginationHelper.toPagedOrListResponse(submittedAbsences, offset, limit, absence -> {
				Resource type = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
				return ChronivaroMapper.toDto(tx, absence, type);
			});
		}
	}

	@POST
	@Path("absences/{id}/approve")
	@Produces(MediaType.APPLICATION_JSON)
	public Response approveAbsence(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
			ConcurrencyHelper.validateIfMatch(request, absence);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new ApproveAbsenceService(), new StringArgument(id));
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
	@Path("absences/{id}/reject")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response rejectAbsence(
			@Context HttpServletRequest request,
			@PathParam("id") String id,
			String data) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);

		PeriodActionRequestDto dto = isNotEmpty(data) ?
				ChronivaroRestHelper.createGson().fromJson(data, PeriodActionRequestDto.class) : null;

		String comment = dto != null ? dto.comment() : null;
		if (isEmpty(comment)) {
			return ChronivaroRestHelper.toErrorResponse(Response.Status.BAD_REQUEST, "VALIDATION_ERROR",
					"Rejection reason must be provided in comment");
		}

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
			ConcurrencyHelper.validateIfMatch(request, absence);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new RejectAbsenceService(),
				new RejectAbsenceService.RejectAbsenceArgument(id, comment));
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource absence = tx.getResourceBy(TYPE_ABSENCE, id, true);
				Resource type = tx.getResourceByRelation(absence, PARAM_ABSENCE_TYPE, true);
				return ConcurrencyHelper.toResponseWithETag(absence, ChronivaroMapper.toDto(tx, absence, type));
			}
		}
		return ChronivaroRestHelper.toResponse(result);
	}
}
