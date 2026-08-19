package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.PeriodHelper;
import ch.atexxi.chronivaro.core.model.VacationHelper;
import ch.atexxi.chronivaro.core.report.CsvExportHelper;
import ch.atexxi.chronivaro.core.service.*;
import ch.atexxi.chronivaro.rest.dto.ChronivaroMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.base.AccessDeniedException;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.StrolchRestfulConstants;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

@Path("/chronivaro/v1/reports")
@Consumes(MediaType.APPLICATION_JSON)
public class ReportsResource {

	@Context
	private HttpServletRequest request;

	private Certificate getCertificate() {
		return (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
	}

	private ServiceHandler getServiceHandler() {
		return ChronivaroRestHelper.getServiceHandler();
	}

	private boolean isCsv(String format, String acceptHeader) {
		return "csv".equalsIgnoreCase(format) || (acceptHeader != null && acceptHeader.contains("text/csv"));
	}

	private String resolveTargetEmployeeId(StrolchTransaction tx, Certificate cert, String requestedEmployeeId) {
		if (requestedEmployeeId != null && !requestedEmployeeId.trim().isEmpty()) {
			String targetId = requestedEmployeeId.trim();
			assertCanAccessEmployeeReport(tx, cert, targetId);
			return targetId;
		}

		Optional<Resource> emp = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
		if (emp.isEmpty()) {
			throw new AccessDeniedException("Access denied: No employee profile linked to user " + cert.getUsername());
		}
		return emp.get().getId();
	}

	private void assertCanAccessEmployeeReport(StrolchTransaction tx, Certificate cert, String targetEmployeeId) {
		if (tx.getPrivilegeContext().hasRole(ROLE_HR)
				|| tx.getPrivilegeContext().hasRole(ROLE_ADMIN)
				|| tx.getPrivilegeContext().hasRole(ROLE_ADMINISTRATOR)) {
			return;
		}

		Optional<Resource> callerEmp = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
		String callerEmpId = callerEmp.map(Resource::getId).orElse(null);

		if (callerEmpId != null && callerEmpId.equals(targetEmployeeId)) {
			return;
		}

		if (tx.getPrivilegeContext().hasRole(ROLE_SUPERVISOR)) {
			List<String> supervised = ChronivaroModelHelper.getSupervisedEmployeeIds(tx, cert);
			if (supervised.contains(targetEmployeeId)) {
				return;
			}
		}

		throw new AccessDeniedException("Access denied: You do not have permission to view reports for employee " + targetEmployeeId);
	}

	@GET
	@Path("/day")
	@Produces({MediaType.APPLICATION_JSON, "text/csv"})
	public Response getDayReport(@QueryParam("date") String dateStr,
								 @QueryParam("employeeId") String employeeId,
								 @QueryParam("format") String format,
								 @HeaderParam("Accept") String acceptHeader) {
		if (dateStr == null || dateStr.trim().isEmpty()) {
			throw new IllegalArgumentException("date query parameter is required (format: YYYY-MM-DD)");
		}

		LocalDate date = LocalDate.parse(dateStr.trim());
		Certificate cert = getCertificate();

		String targetEmployeeId;
		Resource employeeResource;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			targetEmployeeId = resolveTargetEmployeeId(tx, cert, employeeId);
			employeeResource = ChronivaroModelHelper.getEmployee(tx, targetEmployeeId);
		}

		DaySummaryService.DaySummaryArgument arg = new DaySummaryService.DaySummaryArgument();
		arg.employeeId = targetEmployeeId;
		arg.date = date;

		DaySummaryService.DaySummaryResult result = getServiceHandler().doService(cert, new DaySummaryService(), arg);
		if (!result.isOk()) {
			return handleServiceError(result);
		}

		if (isCsv(format, acceptHeader)) {
			String csv = CsvExportHelper.exportDayReportToCsv(result.daySummary, employeeResource);
			return Response.ok(csv, "text/csv; charset=utf-8")
					.header("Content-Disposition", "attachment; filename=\"day-report-" + targetEmployeeId + "-" + dateStr + ".csv\"")
					.build();
		}

		return Response.ok(ChronivaroRestHelper.createGson().toJson(ChronivaroMapper.toDto(result.daySummary)), MediaType.APPLICATION_JSON).build();
	}

	@GET
	@Path("/month")
	@Produces({MediaType.APPLICATION_JSON, "text/csv"})
	public Response getMonthReport(@QueryParam("yearMonth") String yearMonthStr,
								   @QueryParam("employeeId") String employeeId,
								   @QueryParam("format") String format,
								   @HeaderParam("Accept") String acceptHeader) {
		if (yearMonthStr == null || yearMonthStr.trim().isEmpty()) {
			throw new IllegalArgumentException("yearMonth query parameter is required (format: YYYY-MM)");
		}

		YearMonth yearMonth = YearMonth.parse(yearMonthStr.trim());
		Certificate cert = getCertificate();

		String targetEmployeeId;
		Resource employeeResource;
		String periodState = STATE_OPEN;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			targetEmployeeId = resolveTargetEmployeeId(tx, cert, employeeId);
			employeeResource = ChronivaroModelHelper.getEmployee(tx, targetEmployeeId);
			Optional<Resource> period = PeriodHelper.findPeriod(tx, targetEmployeeId, yearMonth);
			if (period.isPresent()) {
				periodState = period.get().getString(PARAM_STATE);
			}
		}

		MonthSummaryService.MonthSummaryArgument arg = new MonthSummaryService.MonthSummaryArgument();
		arg.employeeId = targetEmployeeId;
		arg.yearMonth = yearMonth;

		MonthSummaryService.MonthSummaryResult result = getServiceHandler().doService(cert, new MonthSummaryService(), arg);
		if (!result.isOk()) {
			return handleServiceError(result);
		}

		if (isCsv(format, acceptHeader)) {
			String csv = CsvExportHelper.exportMonthReportToCsv(result.monthSummary, periodState, employeeResource);
			return Response.ok(csv, "text/csv; charset=utf-8")
					.header("Content-Disposition", "attachment; filename=\"month-report-" + targetEmployeeId + "-" + yearMonthStr + ".csv\"")
					.build();
		}

		return Response.ok(ChronivaroRestHelper.createGson().toJson(ChronivaroMapper.toDto(result.monthSummary)), MediaType.APPLICATION_JSON).build();
	}

	@GET
	@Path("/vacation")
	@Produces({MediaType.APPLICATION_JSON, "text/csv"})
	public Response getVacationReport(@QueryParam("year") Integer yearParam,
									  @QueryParam("employeeId") String employeeId,
									  @QueryParam("format") String format,
									  @HeaderParam("Accept") String acceptHeader) {
		int year = yearParam != null ? yearParam : LocalDate.now().getYear();
		Certificate cert = getCertificate();

		String targetEmployeeId;
		Resource employeeResource;
		List<Resource> entries;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			targetEmployeeId = resolveTargetEmployeeId(tx, cert, employeeId);
			employeeResource = ChronivaroModelHelper.getEmployee(tx, targetEmployeeId);
			entries = VacationHelper.getVacationEntries(tx, targetEmployeeId, year);
		}

		GetVacationAccountSummaryService.GetVacationAccountSummaryArgument arg = new GetVacationAccountSummaryService.GetVacationAccountSummaryArgument();
		arg.employeeId = targetEmployeeId;
		arg.year = year;

		GetVacationAccountSummaryService.GetVacationAccountSummaryResult result = getServiceHandler().doService(cert, new GetVacationAccountSummaryService(), arg);
		if (!result.isOk()) {
			return handleServiceError(result);
		}

		if (isCsv(format, acceptHeader)) {
			String csv = CsvExportHelper.exportVacationReportToCsv(result.summary, entries, employeeResource, year);
			return Response.ok(csv, "text/csv; charset=utf-8")
					.header("Content-Disposition", "attachment; filename=\"vacation-report-" + targetEmployeeId + "-" + year + ".csv\"")
					.build();
		}

		return Response.ok(ChronivaroRestHelper.createGson().toJson(ChronivaroMapper.vacationSummaryToDto(result.summary, entries)), MediaType.APPLICATION_JSON).build();
	}

	@GET
	@Path("/team")
	@Produces({MediaType.APPLICATION_JSON, "text/csv"})
	public Response getTeamReport(@QueryParam("teamId") String teamId,
								  @QueryParam("yearMonth") String yearMonthStr,
								  @QueryParam("format") String format,
								  @HeaderParam("Accept") String acceptHeader) {
		if (teamId == null || teamId.trim().isEmpty()) {
			throw new IllegalArgumentException("teamId query parameter is required");
		}
		if (yearMonthStr == null || yearMonthStr.trim().isEmpty()) {
			throw new IllegalArgumentException("yearMonth query parameter is required (format: YYYY-MM)");
		}

		YearMonth yearMonth = YearMonth.parse(yearMonthStr.trim());
		Certificate cert = getCertificate();

		TeamReportService.TeamReportArgument arg = new TeamReportService.TeamReportArgument();
		arg.teamId = teamId.trim();
		arg.yearMonth = yearMonth;

		TeamReportService.TeamReportResult result = getServiceHandler().doService(cert, new TeamReportService(), arg);
		if (!result.isOk()) {
			return handleServiceError(result);
		}

		if (isCsv(format, acceptHeader)) {
			String csv = CsvExportHelper.exportTeamReportToCsv(result.teamReport);
			return Response.ok(csv, "text/csv; charset=utf-8")
					.header("Content-Disposition", "attachment; filename=\"team-report-" + teamId.trim() + "-" + yearMonthStr + ".csv\"")
					.build();
		}

		return Response.ok(ChronivaroRestHelper.createGson().toJson(ChronivaroMapper.teamReportToDto(result.teamReport)), MediaType.APPLICATION_JSON).build();
	}

	@GET
	@Path("/absences")
	@Produces({MediaType.APPLICATION_JSON, "text/csv"})
	public Response getAbsencesReport(@QueryParam("teamId") String teamId,
									  @QueryParam("employeeId") String employeeId,
									  @QueryParam("from") String fromStr,
									  @QueryParam("to") String toStr,
									  @QueryParam("absenceTypeCode") String absenceTypeCode,
									  @QueryParam("status") String statusStr,
									  @QueryParam("format") String format,
									  @HeaderParam("Accept") String acceptHeader) {
		Certificate cert = getCertificate();

		AbsenceReportService.AbsenceReportArgument arg = new AbsenceReportService.AbsenceReportArgument();
		if (teamId != null && !teamId.trim().isEmpty()) {
			arg.teamId = teamId.trim();
		}
		if (employeeId != null && !employeeId.trim().isEmpty()) {
			arg.employeeId = employeeId.trim();
		}
		if (fromStr != null && !fromStr.trim().isEmpty()) {
			arg.from = LocalDate.parse(fromStr.trim());
		}
		if (toStr != null && !toStr.trim().isEmpty()) {
			arg.to = LocalDate.parse(toStr.trim());
		}
		if (absenceTypeCode != null && !absenceTypeCode.trim().isEmpty()) {
			arg.absenceTypeCode = absenceTypeCode.trim();
		}
		if (statusStr != null && !statusStr.trim().isEmpty()) {
			arg.state = statusStr.trim();
		}

		AbsenceReportService.AbsenceReportResult result = getServiceHandler().doService(cert, new AbsenceReportService(), arg);
		if (!result.isOk()) {
			return handleServiceError(result);
		}

		if (isCsv(format, acceptHeader)) {
			String csv = CsvExportHelper.exportAbsenceReportToCsv(result.items);
			return Response.ok(csv, "text/csv; charset=utf-8")
					.header("Content-Disposition", "attachment; filename=\"absences-report.csv\"")
					.build();
		}

		return Response.ok(ChronivaroRestHelper.createGson().toJson(ChronivaroMapper.absenceReportToDto(result.items)), MediaType.APPLICATION_JSON).build();
	}

	private Response handleServiceError(ServiceResult result) {
		if (result.getRootCause() instanceof AccessDeniedException) {
			throw (AccessDeniedException) result.getRootCause();
		}
		if (result.getRootCause() instanceof IllegalArgumentException) {
			throw (IllegalArgumentException) result.getRootCause();
		}
		return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
				.entity(result.getMessage())
				.build();
	}
}
