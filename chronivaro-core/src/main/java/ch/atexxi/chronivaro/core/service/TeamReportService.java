package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.*;
import ch.atexxi.chronivaro.core.report.TeamReport;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.base.AccessDeniedException;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class TeamReportService extends AbstractService<TeamReportService.TeamReportArgument, TeamReportService.TeamReportResult> {

	public static class TeamReportArgument extends ServiceArgument {
		public String teamId;
		public YearMonth yearMonth;
	}

	public static class TeamReportResult extends ServiceResult {
		public TeamReport teamReport;

		public TeamReportResult(TeamReport teamReport) {
			super(ServiceResult.success().getState());
			this.teamReport = teamReport;
		}

		public TeamReportResult() {
		}
	}

	@Override
	protected TeamReportResult internalDoService(TeamReportArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("teamId must be set", arg.teamId);
		DBC.PRE.assertNotNull("yearMonth must be set", arg.yearMonth);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource team = ChronivaroModelHelper.getTeam(tx, arg.teamId);
			assertCanAccessTeamReport(tx, team);

			List<Resource> employees = ChronivaroModelHelper.findEmployeesByTeam(tx, arg.teamId);
			List<TeamReport.TeamEmployeeSummary> summaries = new ArrayList<>();

			for (Resource employee : employees) {
				String empId = employee.getId();
				MonthSummary monthSummary = MonthSummaryService.getMonthSummary(tx, empId, arg.yearMonth);

				Optional<Resource> periodOpt = PeriodHelper.findPeriod(tx, empId, arg.yearMonth);
				String periodState = periodOpt.map(p -> p.getString(PARAM_STATE)).orElse(STATE_OPEN);

				int missingBookings = 0;
				for (int day = 1; day <= arg.yearMonth.lengthOfMonth(); day++) {
					LocalDate date = arg.yearMonth.atDay(day);
					if (!ChronivaroModelHelper.isEmployeeActive(employee, date)) {
						continue;
					}

					int target = ScheduleHelper.getTargetMinutes(tx, empId, date);
					DaySummary daySum = monthSummary.daySummaries().get(day - 1);
					if (target > 0 && daySum.actualMinutes() == 0 && daySum.holidayMinutes() == 0 && daySum.absenceMinutes() == 0) {
						missingBookings++;
					}
				}

				summaries.add(new TeamReport.TeamEmployeeSummary(
						empId,
						employee.getName(),
						arg.teamId,
						arg.yearMonth,
						monthSummary.totalTargetMinutes(),
						monthSummary.totalActualMinutes(),
						monthSummary.totalHolidayMinutes(),
						monthSummary.totalAbsenceMinutes(),
						monthSummary.initialBalanceMinutes(),
						monthSummary.getPeriodBalance(),
						monthSummary.getEndBalance(),
						periodState,
						missingBookings
				));
			}

			TeamReport report = new TeamReport(team.getId(), team.getName(), arg.yearMonth, summaries);
			return new TeamReportResult(report);
		}
	}

	private void assertCanAccessTeamReport(StrolchTransaction tx, Resource team) {
		if (tx.getPrivilegeContext().hasRole(ROLE_HR)
				|| tx.getPrivilegeContext().hasRole(ROLE_ADMIN)
				|| tx.getPrivilegeContext().hasRole(ROLE_ADMINISTRATOR)) {
			return;
		}

		String userId = tx.getCertificate().getUserId();
		String username = tx.getCertificate().getUsername();
		Optional<Resource> callerEmp = ChronivaroModelHelper.findEmployeeByUser(tx, userId);
		String callerEmpId = callerEmp.map(Resource::getId).orElse(null);

		boolean isLeader = false;
		if (team.hasRelation(PARAM_LEADER) && callerEmpId != null && callerEmpId.equals(team.getRelationId(PARAM_LEADER))) {
			isLeader = true;
		} else if (team.hasParameter(PARAM_LEADER)) {
			String leader = team.getString(PARAM_LEADER);
			if (leader.equals(callerEmpId) || leader.equals(username) || leader.equals(userId)) {
				isLeader = true;
			}
		}

		if (isLeader) {
			return;
		}

		if (tx.getPrivilegeContext().hasRole(ROLE_SUPERVISOR)) {
			List<String> supervised = ChronivaroModelHelper.getSupervisedEmployeeIds(tx, tx.getCertificate());
			List<Resource> teamEmps = ChronivaroModelHelper.findEmployeesByTeam(tx, team.getId());
			boolean hasSupervisedMember = teamEmps.stream().anyMatch(e -> supervised.contains(e.getId()));
			if (hasSupervisedMember) {
				return;
			}
		}

		throw new AccessDeniedException("Access denied: You do not have permission to view reports for team " + team.getId());
	}

	@Override
	public TeamReportArgument getArgumentInstance() {
		return new TeamReportArgument();
	}

	@Override
	public TeamReportResult getResultInstance() {
		return new TeamReportResult();
	}
}
