package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.WorkEntryHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import java.util.List;
import java.util.Optional;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class PresenceService extends AbstractService<PresenceService.PresenceArgument, PresenceService.PresenceResult> {

	public enum PresenceStatus {
		WORKING, NOT_WORKING
	}

	public record PresenceInfo(String employeeId, String displayName, PresenceStatus status) {}

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
			List<PresenceInfo> presenceInfos = tx.streamResources(TYPE_EMPLOYEE)
					.filter(e -> arg.teamId == null || e.getString(BAG_RELATIONS, TYPE_TEAM).equals(arg.teamId))
					.filter(e -> arg.locationId == null || e.getString(BAG_RELATIONS, TYPE_LOCATION).equals(arg.locationId))
					.filter(e -> e.getBoolean(PARAM_ACTIVE))
					.map(e -> {
						Optional<Resource> activeEntry = WorkEntryHelper.findActiveWorkEntry(tx, e.getId());
						PresenceStatus status = activeEntry.isPresent() ? PresenceStatus.WORKING : PresenceStatus.NOT_WORKING;
						return new PresenceInfo(e.getId(), e.getString(PARAM_DISPLAY_NAME), status);
					})
					.toList();

			return new PresenceResult(presenceInfos);
		}
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
