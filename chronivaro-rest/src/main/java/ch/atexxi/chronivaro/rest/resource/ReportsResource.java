package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.PeriodHelper;
import ch.atexxi.chronivaro.core.model.VacationHelper;
import ch.atexxi.chronivaro.core.report.CsvExportHelper;
import ch.atexxi.chronivaro.core.report.PdfExportHelper;
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
import java.time.ZonedDateTime;
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

	private boolean isPdf(String format, String acceptHeader) {
		return "pdf".equalsIgnoreCase(format) || (acceptHeader != null && acceptHeader.contains("application/pdf"));
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
	@Produces({MediaType.APPLICATION_JSON, "text/csv", "application/pdf"})
	public Response getMonthReport(@QueryParam("yearMonth") String yearMonthStr,
								   @QueryParam("employeeId") String employeeId,
								   @QueryParam("format") String format,
								   @QueryParam("lang") String lang,
								   @HeaderParam("Accept") String acceptHeader) {
		if (yearMonthStr == null || yearMonthStr.trim().isEmpty()) {
			throw new IllegalArgumentException("yearMonth query parameter is required (format: YYYY-MM)");
		}

		YearMonth yearMonth = YearMonth.parse(yearMonthStr.trim());
		Certificate cert = getCertificate();

		String targetEmployeeId;
		Resource employeeResource;
		String periodState = STATE_OPEN;
		ZonedDateTime approvalDate = null;
		String approvedBy = null;
		Resource companyConfig;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			targetEmployeeId = resolveTargetEmployeeId(tx, cert, employeeId);
			employeeResource = ChronivaroModelHelper.getEmployee(tx, targetEmployeeId);
			companyConfig = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", false);
			Optional<Resource> period = PeriodHelper.findPeriod(tx, targetEmployeeId, yearMonth);
			if (period.isPresent()) {
				periodState = period.get().getString(PARAM_STATE);
				if (period.get().hasParameter(PARAM_APPROVED_AT)) {
					approvalDate = period.get().getDate(PARAM_APPROVED_AT);
				}
				if (period.get().hasParameter(PARAM_APPROVED_BY)) {
					approvedBy = period.get().getString(PARAM_APPROVED_BY);
				}
			}
		}

		MonthSummaryService.MonthSummaryArgument arg = new MonthSummaryService.MonthSummaryArgument();
		arg.employeeId = targetEmployeeId;
		arg.yearMonth = yearMonth;

		MonthSummaryService.MonthSummaryResult result = getServiceHandler().doService(cert, new MonthSummaryService(), arg);
		if (!result.isOk()) {
			return handleServiceError(result);
		}

		if (isPdf(format, acceptHeader)) {
			byte[] pdf = PdfExportHelper.exportMonthReportToPdf(result.monthSummary, periodState, approvalDate, approvedBy, employeeResource, companyConfig, lang);
			String filename = PdfExportHelper.getMonthReportPdfFileName(targetEmployeeId, yearMonth);
			return Response.ok(pdf, "application/pdf")
					.header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
					.build();
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
	@Path("/month.pdf")
	@Produces("application/pdf")
	public Response getMonthReportPdfAlias(@QueryParam("yearMonth") String yearMonthStr,
										   @QueryParam("employeeId") String employeeId,
										   @QueryParam("lang") String lang) {
		return getMonthReport(yearMonthStr, employeeId, "pdf", lang, "application/pdf");
	}

	@GET
	@Path("/vacation")
	@Produces({MediaType.APPLICATION_JSON, "text/csv", "application/pdf"})
	public Response getVacationReport(@QueryParam("year") Integer yearParam,
									  @QueryParam("employeeId") String employeeId,
									  @QueryParam("format") String format,
									  @QueryParam("lang") String lang,
									  @HeaderParam("Accept") String acceptHeader) {
		int year = yearParam != null ? yearParam : LocalDate.now().getYear();
		Certificate cert = getCertificate();

		String targetEmployeeId;
		Resource employeeResource;
		List<Resource> entries;
		Resource companyConfig;
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			targetEmployeeId = resolveTargetEmployeeId(tx, cert, employeeId);
			employeeResource = ChronivaroModelHelper.getEmployee(tx, targetEmployeeId);
			companyConfig = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", false);
			entries = VacationHelper.getVacationEntries(tx, targetEmployeeId, year);
		}

		GetVacationAccountSummaryService.GetVacationAccountSummaryArgument arg = new GetVacationAccountSummaryService.GetVacationAccountSummaryArgument();
		arg.employeeId = targetEmployeeId;
		arg.year = year;

		GetVacationAccountSummaryService.GetVacationAccountSummaryResult result = getServiceHandler().doService(cert, new GetVacationAccountSummaryService(), arg);
		if (!result.isOk()) {
			return handleServiceError(result);
		}

		if (isPdf(format, acceptHeader)) {
			byte[] pdf = PdfExportHelper.exportVacationReportToPdf(result.summary, entries, employeeResource, year, companyConfig, lang);
			String filename = PdfExportHelper.getVacationReportPdfFileName(targetEmployeeId, year);
			return Response.ok(pdf, "application/pdf")
					.header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
					.build();
		}

		if (isCsv(format, acceptHeader)) {
			String csv = CsvExportHelper.exportVacationReportToCsv(result.summary, entries, employeeResource, year);
			return Response.ok(csv, "text/csv; charset=utf-8")
					.header("Content-Disposition", "attachment; filename=\"vacation-report-" + targetEmployeeId + "-" + year + ".csv\"")
					.build();
		}

		return Response.ok(ChronivaroRestHelper.createGson().toJson(ChronivaroMapper.vacationSummaryToDto(employeeResource, result.summary, entries)), MediaType.APPLICATION_JSON).build();
	}

	@GET
	@Path("/vacation.pdf")
	@Produces("application/pdf")
	public Response getVacationReportPdfAlias(@QueryParam("year") Integer yearParam,
											  @QueryParam("employeeId") String employeeId,
											  @QueryParam("lang") String lang) {
		return getVacationReport(yearParam, employeeId, "pdf", lang, "application/pdf");
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
	@Produces({MediaType.APPLICATION_JSON, "text/csv", "application/pdf"})
	public Response getAbsencesReport(@QueryParam("teamId") String teamId,
									  @QueryParam("employeeId") String employeeId,
									  @QueryParam("from") String fromStr,
									  @QueryParam("to") String toStr,
									  @QueryParam("absenceTypeCode") String absenceTypeCode,
									  @QueryParam("status") String statusStr,
									  @QueryParam("format") String format,
									  @QueryParam("lang") String lang,
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

		if (isPdf(format, acceptHeader)) {
			String teamName = null;
			String employeeName = null;
			String targetEmployeeId = employeeId;
			Resource companyConfig;
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				companyConfig = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", false);
				if (teamId != null && !teamId.trim().isEmpty()) {
					Resource teamRes = tx.getResourceBy(TYPE_TEAM, teamId.trim());
					if (teamRes != null) {
						teamName = teamRes.getName();
					}
				}
				if (targetEmployeeId != null && !targetEmployeeId.trim().isEmpty()) {
					Resource empRes = tx.getResourceBy(TYPE_EMPLOYEE, targetEmployeeId.trim());
					if (empRes != null) {
						employeeName = empRes.getName();
					}
				} else if (teamId == null || teamId.trim().isEmpty()) {
					Optional<Resource> callerEmp = ChronivaroModelHelper.findEmployeeByUser(tx, cert.getUserId());
					if (callerEmp.isPresent() && !tx.getPrivilegeContext().hasRole(ROLE_HR) && !tx.getPrivilegeContext().hasRole(ROLE_ADMIN) && !tx.getPrivilegeContext().hasRole(ROLE_ADMINISTRATOR) && !tx.getPrivilegeContext().hasRole(ROLE_SUPERVISOR)) {
						employeeName = callerEmp.get().getName();
						targetEmployeeId = callerEmp.get().getId();
					}
				}
			}

			LocalDate fromDate = (fromStr != null && !fromStr.trim().isEmpty()) ? LocalDate.parse(fromStr.trim()) : null;
			LocalDate toDate = (toStr != null && !toStr.trim().isEmpty()) ? LocalDate.parse(toStr.trim()) : null;
			byte[] pdf = PdfExportHelper.exportAbsenceReportToPdf(result.items, teamName, employeeName, fromDate, toDate, companyConfig, lang);
			String context = targetEmployeeId != null && !targetEmployeeId.isBlank() ? targetEmployeeId : (teamId != null && !teamId.isBlank() ? teamId : "all");
			String filename = PdfExportHelper.getAbsenceReportPdfFileName(context, fromDate, toDate);
			return Response.ok(pdf, "application/pdf")
					.header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
					.build();
		}

		if (isCsv(format, acceptHeader)) {
			String csv = CsvExportHelper.exportAbsenceReportToCsv(result.items);
			return Response.ok(csv, "text/csv; charset=utf-8")
					.header("Content-Disposition", "attachment; filename=\"absences-report.csv\"")
					.build();
		}

		return Response.ok(ChronivaroRestHelper.createGson().toJson(ChronivaroMapper.absenceReportToDto(result.items)), MediaType.APPLICATION_JSON).build();
	}

	@GET
	@Path("/absences.pdf")
	@Produces("application/pdf")
	public Response getAbsencesReportPdfAlias(@QueryParam("teamId") String teamId,
											  @QueryParam("employeeId") String employeeId,
											  @QueryParam("from") String fromStr,
											  @QueryParam("to") String toStr,
											  @QueryParam("absenceTypeCode") String absenceTypeCode,
											  @QueryParam("status") String statusStr,
											  @QueryParam("lang") String lang) {
		return getAbsencesReport(teamId, employeeId, fromStr, toStr, absenceTypeCode, statusStr, "pdf", lang, "application/pdf");
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
