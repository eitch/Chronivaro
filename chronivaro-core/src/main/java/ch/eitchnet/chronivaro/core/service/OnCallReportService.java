package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.WorkEntryHelper;
import ch.eitchnet.chronivaro.core.report.OnCallReport;
import ch.eitchnet.chronivaro.core.report.OnCallReport.OnCallPeriodItem;
import ch.eitchnet.chronivaro.core.report.OnCallReport.OnCallWorkEntryItem;
import ch.eitchnet.chronivaro.core.search.OnCallPeriodSearch;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.base.AccessDeniedException;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.getVersion;

public class OnCallReportService extends AbstractService<OnCallReportService.OnCallReportArgument, OnCallReportService.OnCallReportResult> {

	public static class OnCallReportArgument extends ServiceArgument {
		public String teamId;
		public String employeeId;
		public LocalDate from;
		public LocalDate to;
		public YearMonth yearMonth;
	}

	public static class OnCallReportResult extends ServiceResult {
		public OnCallReport report;

		public OnCallReportResult(OnCallReport report) {
			super(ServiceResult.success().getState());
			this.report = report;
		}

		public OnCallReportResult() {
		}
	}

	@Override
	protected OnCallReportResult internalDoService(OnCallReportArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			List<String> targetEmployeeIds = resolveTargetEmployeeIds(tx, arg);
			if (targetEmployeeIds.isEmpty()) {
				LocalDate from = resolveFrom(arg);
				LocalDate to = resolveTo(arg);
				return new OnCallReportResult(new OnCallReport("on-call", from, to, Collections.emptyList(), Collections.emptyList(), 0, 0, 0));
			}

			LocalDate fromDate = resolveFrom(arg);
			LocalDate toDate = resolveTo(arg);

			// 1. On-Call Periods
			OnCallPeriodSearch search = new OnCallPeriodSearch();
			search.forEmployees(targetEmployeeIds);
			List<Resource> periods = search.searchPeriods(tx);

			List<OnCallPeriodItem> periodItems = new ArrayList<>();
			for (Resource period : periods) {
				LocalDate start = period.getDate(PARAM_START).toLocalDate();
				LocalDate end = period.getDate(PARAM_END).toLocalDate();

				if (fromDate != null && end.isBefore(fromDate))
					continue;
				if (toDate != null && start.isAfter(toDate))
					continue;

				String empId = period.getRelationId(PARAM_EMPLOYEE);
				Resource emp = tx.getResourceBy(TYPE_EMPLOYEE, empId, false);
				String empName = emp != null ? emp.getName() : empId;

				String startTime = period.hasParameter(PARAM_START_TIME) ? period.getString(PARAM_START_TIME) : "00:00";
				String endTime = period.hasParameter(PARAM_END_TIME) ? period.getString(PARAM_END_TIME) : "23:59";
				String comment = period.hasParameter(PARAM_COMMENT) ? period.getString(PARAM_COMMENT) : "";
				String createdBy = period.hasParameter(PARAM_CREATED_BY) ? period.getString(PARAM_CREATED_BY) : "";

				periodItems.add(new OnCallPeriodItem(
						period.getId(),
						empId,
						empName,
						start,
						startTime,
						end,
						endTime,
						comment,
						createdBy
				));
			}
			periodItems.sort(Comparator.comparing(OnCallPeriodItem::startDate).thenComparing(OnCallPeriodItem::startTime));

			// 2. On-Call Work Entries
			List<OnCallWorkEntryItem> workEntryItems = new ArrayList<>();
			int totalDurationMinutes = 0;

			for (String empId : targetEmployeeIds) {
				Resource emp = tx.getResourceBy(TYPE_EMPLOYEE, empId, false);
				if (emp == null)
					continue;
				ZoneId zone = ChronivaroModelHelper.getEmployeeTimezone(emp);
				String empName = emp.getName();

				ZonedDateTime searchFrom = (fromDate != null ? fromDate : LocalDate.now().minusMonths(1)).atStartOfDay(zone);
				ZonedDateTime searchTo = (toDate != null ? toDate : LocalDate.now().plusMonths(1)).plusDays(1).atStartOfDay(zone).minusNanos(1);

				List<Resource> entries = WorkEntryHelper.findWorkEntries(tx, empId, searchFrom, searchTo);
				for (Resource entry : entries) {
					boolean isOnCall = entry.hasParameter(PARAM_IS_ON_CALL) && entry.getBoolean(PARAM_IS_ON_CALL);
					if (!isOnCall)
						continue;

					ZonedDateTime start = entry.getDate(PARAM_START);
					ZonedDateTime end = entry.getDate(PARAM_END);
					LocalDate date = start.toLocalDate();

					if (fromDate != null && date.isBefore(fromDate))
						continue;
					if (toDate != null && date.isAfter(toDate))
						continue;

					boolean isActive = end.getYear() == 1970;
					ZonedDateTime effectiveEnd = isActive ? ZonedDateTime.now(start.getZone()) : end;
					int duration = (int) java.time.Duration.between(start, effectiveEnd).toMinutes();
					if (duration < 0)
						duration = 0;

					totalDurationMinutes += duration;

					String source = entry.hasParameter(PARAM_SOURCE) ? entry.getString(PARAM_SOURCE) : "";
					String comment = entry.hasParameter(PARAM_COMMENT) ? entry.getString(PARAM_COMMENT) : "";
					String createdBy = entry.hasParameter(PARAM_CREATED_BY) ? entry.getString(PARAM_CREATED_BY) : "";
					boolean modified = getVersion(entry) > 0;

					workEntryItems.add(new OnCallWorkEntryItem(
							entry.getId(),
							empId,
							empName,
							date,
							start,
							isActive ? null : end,
							duration,
							source,
							comment,
							createdBy,
							modified
					));
				}
			}
			workEntryItems.sort(Comparator.comparing(OnCallWorkEntryItem::start).reversed());

			String context = arg.employeeId != null && !arg.employeeId.isBlank() ? arg.employeeId
					: (arg.teamId != null && !arg.teamId.isBlank() ? arg.teamId : "all");

			OnCallReport report = new OnCallReport(
					context,
					fromDate,
					toDate,
					periodItems,
					workEntryItems,
					periodItems.size(),
					workEntryItems.size(),
					totalDurationMinutes
			);

			return new OnCallReportResult(report);
		}
	}

	private LocalDate resolveFrom(OnCallReportArgument arg) {
		if (arg.from != null)
			return arg.from;
		if (arg.yearMonth != null)
			return arg.yearMonth.atDay(1);
		return LocalDate.now().withDayOfMonth(1);
	}

	private LocalDate resolveTo(OnCallReportArgument arg) {
		if (arg.to != null)
			return arg.to;
		if (arg.yearMonth != null)
			return arg.yearMonth.atEndOfMonth();
		return LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
	}

	private List<String> resolveTargetEmployeeIds(StrolchTransaction tx, OnCallReportArgument arg) {
		boolean isAdminOrHr = tx.getPrivilegeContext().hasRole(ROLE_HR)
				|| tx.getPrivilegeContext().hasRole(ROLE_ADMIN)
				|| tx.getPrivilegeContext().hasRole(ROLE_ADMINISTRATOR);

		if (isAdminOrHr) {
			if (arg.employeeId != null && !arg.employeeId.isEmpty()) {
				return List.of(arg.employeeId);
			}
			if (arg.teamId != null && !arg.teamId.isEmpty()) {
				return ChronivaroModelHelper.findEmployeesByTeam(tx, arg.teamId).stream().map(Resource::getId).toList();
			}
			return tx.streamResources(TYPE_EMPLOYEE).map(Resource::getId).toList();
		}

		if (tx.getPrivilegeContext().hasRole(ROLE_SUPERVISOR)) {
			Set<String> allowed = new HashSet<>(ChronivaroModelHelper.getSupervisedEmployeeIds(tx, tx.getCertificate()));
			Optional<Resource> callerEmp = ChronivaroModelHelper.findEmployeeByUser(tx, tx.getCertificate().getUserId());
			callerEmp.ifPresent(resource -> allowed.add(resource.getId()));

			if (arg.employeeId != null && !arg.employeeId.isEmpty()) {
				if (!allowed.contains(arg.employeeId)) {
					throw new AccessDeniedException("Access denied: Employee " + arg.employeeId + " is not supervised by you.");
				}
				return List.of(arg.employeeId);
			}

			if (arg.teamId != null && !arg.teamId.isEmpty()) {
				List<String> teamEmpIds = ChronivaroModelHelper.findEmployeesByTeam(tx, arg.teamId).stream().map(Resource::getId).toList();
				List<String> filtered = teamEmpIds.stream().filter(allowed::contains).toList();
				if (filtered.isEmpty() && !teamEmpIds.isEmpty()) {
					throw new AccessDeniedException("Access denied: Team " + arg.teamId + " is not supervised by you.");
				}
				return filtered;
			}

			return new ArrayList<>(allowed);
		}

		// Regular Employee
		Optional<Resource> callerEmp = ChronivaroModelHelper.findEmployeeByUser(tx, tx.getCertificate().getUserId());
		if (callerEmp.isEmpty()) {
			throw new AccessDeniedException("Access denied: No employee profile found for user.");
		}
		String callerEmpId = callerEmp.get().getId();

		if (arg.employeeId != null && !arg.employeeId.isEmpty() && !arg.employeeId.equals(callerEmpId)) {
			throw new AccessDeniedException("Access denied: You can only view your own on-call reports.");
		}

		return List.of(callerEmpId);
	}

	@Override
	public OnCallReportArgument getArgumentInstance() {
		return new OnCallReportArgument();
	}

	@Override
	public OnCallReportResult getResultInstance() {
		return new OnCallReportResult();
	}
}
