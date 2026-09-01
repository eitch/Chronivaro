package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.report.AbsenceReportItem;
import ch.eitchnet.chronivaro.core.search.AbsenceSearch;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.base.AccessDeniedException;
import li.strolch.privilege.model.Restrictable;
import li.strolch.privilege.model.SimpleRestrictable;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;

public class AbsenceReportService extends AbstractService<AbsenceReportService.AbsenceReportArgument, AbsenceReportService.AbsenceReportResult> {

	public static final String PRIVILEGE_GET_ABSENCE_REASON = "li.strolch.chronivaro.privilege.GetAbsenceReason";

	public static class AbsenceReportArgument extends ServiceArgument {
		public String teamId;
		public String employeeId;
		public LocalDate from;
		public LocalDate to;
		public String absenceTypeCode;
		public String state;
	}

	public static class AbsenceReportResult extends ServiceResult {
		public List<AbsenceReportItem> items = new ArrayList<>();

		public AbsenceReportResult(List<AbsenceReportItem> items) {
			super(ServiceResult.success().getState());
			this.items = items;
		}

		public AbsenceReportResult() {
		}
	}

	@Override
	protected AbsenceReportResult internalDoService(AbsenceReportArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			List<String> targetEmployeeIds = resolveTargetEmployeeIds(tx, arg);
			if (targetEmployeeIds.isEmpty()) {
				return new AbsenceReportResult(Collections.emptyList());
			}

			AbsenceSearch search = new AbsenceSearch();
			search.forEmployees(targetEmployeeIds);

			if (arg.state != null && !arg.state.isEmpty()) {
				search.forState(arg.state);
			}
			if (arg.absenceTypeCode != null && !arg.absenceTypeCode.isEmpty()) {
				search.forAbsenceType(arg.absenceTypeCode);
			}

			List<Resource> absences = search.search(tx).toList();

			List<AbsenceReportItem> items = new ArrayList<>();
			for (Resource absence : absences) {
				LocalDate start = absence.getDate(PARAM_START).toLocalDate();
				LocalDate end = absence.getDate(PARAM_END).toLocalDate();

				if (arg.from != null && end.isBefore(arg.from)) {
					continue;
				}
				if (arg.to != null && start.isAfter(arg.to)) {
					continue;
				}

				String empId = absence.getRelationId(PARAM_EMPLOYEE);
				Resource emp = tx.getResourceBy(TYPE_EMPLOYEE, empId, false);
				String empName = emp != null ? emp.getName() : empId;

				String typeCode = absence.getString(PARAM_ABSENCE_TYPE);
				Resource absType = tx.getResourceBy(TYPE_ABSENCE_TYPE, typeCode, false);
				String typeName = absType != null ? absType.getName() : typeCode;
				boolean paid = absType != null && absType.hasParameter(PARAM_PAID) && absType.getBoolean(PARAM_PAID);

				boolean isAdminOrHrOrSupervisor = tx.getPrivilegeContext().hasRole(ROLE_HR)
						|| tx.getPrivilegeContext().hasRole(ROLE_ADMIN)
						|| tx.getPrivilegeContext().hasRole(ROLE_ADMINISTRATOR)
						|| tx.getPrivilegeContext().hasRole(ROLE_SUPERVISOR);
				Optional<Resource> currentCallerEmp = ChronivaroModelHelper.findEmployeeByUser(tx, tx.getCertificate().getUserId());
				boolean isOwn = currentCallerEmp.isPresent() && currentCallerEmp.get().getId().equals(empId);

				if (!isAdminOrHrOrSupervisor && !isOwn) {
					boolean visibleOnPublic = absType != null && absType.hasParameter(PARAM_VISIBLE_ON_PUBLIC_STATUS)
							&& absType.getBoolean(PARAM_VISIBLE_ON_PUBLIC_STATUS);
					Restrictable restrictable = new SimpleRestrictable(PRIVILEGE_GET_ABSENCE_REASON, typeCode);
					boolean hasPrivilege = tx.getPrivilegeContext().hasPrivilege(restrictable);
					if (!hasPrivilege && !visibleOnPublic) {
						typeCode = "ABSENT";
						typeName = "Abwesend";
					}
				}

				String durationType = absence.getString(PARAM_DURATION_TYPE);
				String dayPart = absence.hasParameter(PARAM_DAY_PART) && !absence.getString(PARAM_DAY_PART).isEmpty()
						? absence.getString(PARAM_DAY_PART) : null;
				int minutes = absence.hasParameter(PARAM_MINUTES) ? absence.getInteger(PARAM_MINUTES) : 0;
				String state = absence.getString(PARAM_STATE);
				String comment = absence.hasParameter(PARAM_COMMENT) ? absence.getString(PARAM_COMMENT) : "";
				ZonedDateTime submittedAt = absence.hasParameter(PARAM_SUBMITTED_AT) ? absence.getDate(PARAM_SUBMITTED_AT) : null;
				ZonedDateTime approvedAt = absence.hasParameter(PARAM_APPROVED_AT) ? absence.getDate(PARAM_APPROVED_AT) : null;
				String approvedBy = absence.hasParameter(PARAM_APPROVED_BY) ? absence.getString(PARAM_APPROVED_BY) : "";

				items.add(new AbsenceReportItem(
						absence.getId(),
						empId,
						empName,
						typeCode,
						typeName,
						start,
						end,
						durationType,
						dayPart,
						minutes,
						state,
						paid,
						comment,
						submittedAt,
						approvedAt,
						approvedBy
				));
			}

			items.sort(Comparator.comparing(AbsenceReportItem::start).reversed());
			return new AbsenceReportResult(items);
		}
	}

	private List<String> resolveTargetEmployeeIds(StrolchTransaction tx, AbsenceReportArgument arg) {
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

		if (arg.employeeId != null && !arg.employeeId.isEmpty()) {
			if (!arg.employeeId.equals(callerEmpId)) {
				throw new AccessDeniedException("Access denied: You can only view your own absence reports.");
			}
			return List.of(callerEmpId);
		}

		if (arg.teamId != null && !arg.teamId.isEmpty()) {
			String callerTeamId = callerEmp.get().hasRelation(PARAM_PRIMARY_TEAM) ? callerEmp.get().getRelationId(PARAM_PRIMARY_TEAM) : null;
			if (!arg.teamId.equals(callerTeamId)) {
				throw new AccessDeniedException("Access denied: You can only view absences for your own team.");
			}
			return ChronivaroModelHelper.findEmployeesByTeam(tx, arg.teamId).stream().map(Resource::getId).toList();
		}

		return List.of(callerEmpId);
	}

	@Override
	public AbsenceReportArgument getArgumentInstance() {
		return new AbsenceReportArgument();
	}

	@Override
	public AbsenceReportResult getResultInstance() {
		return new AbsenceReportResult();
	}
}
