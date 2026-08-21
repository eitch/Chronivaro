package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.ScheduleHelper;
import ch.atexxi.chronivaro.core.model.WorkEntryHelper;
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
import java.util.List;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class PresenceService extends AbstractService<PresenceService.PresenceArgument, PresenceService.PresenceResult> {

	public static final String PRIVILEGE_GET_ABSENCE_REASON = "li.strolch.chronivaro.privilege.GetAbsenceReason";

	public enum PresenceStatus {
		WORKING("Working"),
		NOT_WORKING("Not working");

		private final String label;

		PresenceStatus(String label) {
			this.label = label;
		}

		public String getLabel() {
			return this.label;
		}
	}

	public record PresenceInfo(String employeeId, String firstname, String lastname, String teamId, String teamName,
							   PresenceStatus status, String statusLabel, int minutesToday, String absenceTypeCode,
							   String absenceTypeName, boolean isOff, String workingLocation) {
	}

	public static class PresenceArgument extends ServiceArgument {
		public String teamId;
		public String locationId;
	}

	public static class PresenceResult extends ServiceResult {
		public List<PresenceInfo> presenceInfos;

		public PresenceResult(List<PresenceInfo> presenceInfos) {
			super(ServiceResult.success().getState());
			this.presenceInfos = presenceInfos;
		}

		public PresenceResult() {
		}
	}

	@Override
	protected PresenceResult internalDoService(PresenceArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			boolean isPrivileged = tx.getPrivilegeContext().hasRole(ROLE_HR)
					|| tx.getPrivilegeContext().hasRole(ROLE_ADMIN)
					|| tx.getPrivilegeContext().hasRole(ROLE_ADMINISTRATOR)
					|| tx.getPrivilegeContext().hasRole(ROLE_SUPERVISOR);

			String effectiveTeamId = arg.teamId;
			if (!isPrivileged) {
				Optional<Resource> callerEmp = ChronivaroModelHelper.findEmployeeByUser(tx, tx.getCertificate().getUserId());
				if (callerEmp.isEmpty() || !callerEmp.get().hasRelation(PARAM_PRIMARY_TEAM)) {
					throw new AccessDeniedException("Access denied: No employee profile or team found for current user.");
				}
				String callerTeamId = callerEmp.get().getRelationId(PARAM_PRIMARY_TEAM);
				if (arg.teamId != null && !arg.teamId.isEmpty() && !arg.teamId.equals(callerTeamId)) {
					throw new AccessDeniedException("Access denied: You can only view presence for your own team.");
				}
				effectiveTeamId = callerTeamId;
			}

			String finalTeamId = effectiveTeamId;
			List<PresenceInfo> presenceInfos = tx
					.streamResources(TYPE_EMPLOYEE)
					.filter(e -> finalTeamId == null || (e.hasRelation(PARAM_PRIMARY_TEAM) && e.getRelationId(PARAM_PRIMARY_TEAM).equals(finalTeamId)))
					.filter(e -> arg.locationId == null || (e.hasRelation(PARAM_LOCATION) && e.getRelationId(PARAM_LOCATION).equals(arg.locationId)))
					.filter(e -> e.getBoolean(PARAM_ACTIVE))
					.map(e -> {
						Optional<Resource> activeEntry = WorkEntryHelper.findActiveWorkEntry(tx, e.getId());
						PresenceStatus status = activeEntry.isPresent() ? PresenceStatus.WORKING :
								PresenceStatus.NOT_WORKING;

						int minutesToday = calculateMinutesToday(tx, e);

						ZonedDateTime now = ZonedDateTime.now(ChronivaroModelHelper.getEmployeeTimezone(e));
						LocalDate today = now.toLocalDate();
						int targetMinutes = ScheduleHelper.getTargetMinutes(tx, e.getId(), today);
						boolean isOff = targetMinutes == 0;

						String absenceTypeCode = null;
						String absenceTypeName = null;

						Optional<Resource> absence = findActiveAbsence(tx, e.getId(), today);
						if (absence.isPresent()) {
							Resource a = absence.get();
							String absenceTypeId = a.getRelationId(PARAM_ABSENCE_TYPE);
							Resource absenceType = tx.getResourceBy(TYPE_ABSENCE_TYPE, absenceTypeId, true);
							boolean visibleOnPublic = absenceType.hasParameter(PARAM_VISIBLE_ON_PUBLIC_STATUS)
									&& absenceType.getBoolean(PARAM_VISIBLE_ON_PUBLIC_STATUS);
							Restrictable restrictable = new SimpleRestrictable(PRIVILEGE_GET_ABSENCE_REASON,
									absenceType.getString(PARAM_CODE));
							boolean hasPrivilege = tx.getPrivilegeContext().hasPrivilege(restrictable);
							if (hasPrivilege || visibleOnPublic) {
								absenceTypeCode = absenceType.getString(PARAM_CODE);
								absenceTypeName = absenceType.getName();
							} else {
								absenceTypeCode = "ABSENT";
								absenceTypeName = "Abwesend";
							}
						}

						String teamId = e.getRelationId(PARAM_PRIMARY_TEAM);
						Resource team = tx.getResourceBy(TYPE_TEAM, teamId, true);
						String teamName = team.getName();

						return new PresenceInfo(e.getId(), e.getString(PARAM_FIRSTNAME), e.getString(PARAM_LASTNAME),
								teamId, teamName, status, status.getLabel(), minutesToday, absenceTypeCode,
								absenceTypeName, isOff, activeEntry.map(entry -> entry.getString(PARAM_WORKING_LOCATION)).orElse(null));
					})
					.toList();

			return new PresenceResult(presenceInfos);
		}
	}

	private Optional<Resource> findActiveAbsence(StrolchTransaction tx, String employeeId, LocalDate date) {
		return tx
				.streamResources(TYPE_ABSENCE)
				.filter(a -> a.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
				.filter(a -> a.getString(PARAM_STATE).equals(STATE_APPROVED))
				.filter(a -> {
					LocalDate start = a.getDate(PARAM_START).toLocalDate();
					LocalDate end = a.getDate(PARAM_END).toLocalDate();
					return !date.isBefore(start) && !date.isAfter(end);
				})
				.findFirst();
	}

	private int calculateMinutesToday(StrolchTransaction tx, Resource employee) {
		ZonedDateTime now = ZonedDateTime.now(ChronivaroModelHelper.getEmployeeTimezone(employee));
		ZonedDateTime from = now.toLocalDate().atStartOfDay(now.getZone());
		ZonedDateTime to = from.plusDays(1).minusNanos(1);

		return WorkEntryHelper.findWorkEntries(tx, employee.getId(), from, to).stream().mapToInt(entry -> {
			ZonedDateTime start = entry.getDate(PARAM_START);
			ZonedDateTime end = entry.getDate(PARAM_END);
			if (end.getYear() == 1970)
				end = ZonedDateTime.now(start.getZone());

			ZonedDateTime effectiveStart = start.isBefore(from) ? from : start;
			ZonedDateTime effectiveEnd = end.isAfter(to) ? to : end;

			if (effectiveEnd.isBefore(effectiveStart))
				return 0;

			return (int) java.time.Duration.between(effectiveStart, effectiveEnd).toMinutes();
		}).sum();
	}

	@Override
	public PresenceArgument getArgumentInstance() {
		return new PresenceArgument();
	}

	@Override
	public PresenceResult getResultInstance() {
		return new PresenceResult();
	}
}
