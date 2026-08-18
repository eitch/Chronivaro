package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

public class CreateTeamService extends AbstractService<CreateTeamService.TeamArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(TeamArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource team = tx.getResourceTemplate(TYPE_TEAM, true);
			team.setName(arg.name);
			team.setString(PARAM_NAME, arg.name);
			initVersion(team, tx);
			tx.add(team);
			ChronivaroAuditHelper.audit(tx, TYPE_TEAM, team.getId(), AUDIT_ACTION_CREATE, "Created team " + arg.name);
			tx.commitOnClose();
		}
		return ServiceResult.success();
	}

	@Override
	public TeamArgument getArgumentInstance() {
		return new TeamArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	public static class TeamArgument extends ServiceArgument {
		public String name;
	}

	public static class UpdateTeamArgument extends TeamArgument {
		public String id;
	}
}
