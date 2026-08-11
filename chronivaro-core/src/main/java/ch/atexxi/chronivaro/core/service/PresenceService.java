package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.WorkEntryHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import java.time.ZonedDateTime;

import java.util.List;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class PresenceService extends AbstractService<PresenceService.PresenceArgument, PresenceService.PresenceResult> {

	public enum PresenceStatus {
		WORKING,
		NOT_WORKING
	}

	public record PresenceInfo(String employeeId, String displayName, PresenceStatus status, int minutesToday) {
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
			List<PresenceInfo> presenceInfos = tx
					.streamResources(TYPE_EMPLOYEE)
					.filter(e -> arg.teamId == null || e.getRelationId(PARAM_PRIMARY_TEAM).equals(arg.teamId))
					.filter(e -> arg.locationId == null || e.getRelationId(PARAM_LOCATION).equals(arg.locationId))
					.filter(e -> e.getBoolean(PARAM_ACTIVE))
					.map(e -> {
						Optional<Resource> activeEntry = WorkEntryHelper.findActiveWorkEntry(tx, e.getId());
						PresenceStatus status = activeEntry.isPresent() ? PresenceStatus.WORKING :
								PresenceStatus.NOT_WORKING;

						int minutesToday = calculateMinutesToday(tx, e);

						return new PresenceInfo(e.getId(), e.getString(PARAM_DISPLAY_NAME), status, minutesToday);
					})
					.toList();

			return new PresenceResult(presenceInfos);
		}
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
