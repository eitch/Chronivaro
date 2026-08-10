package ch.atexxi.chronivaro.core.service;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.agent.api.StrolchAgent.getUniqueId;

public class CreateTeamService extends AbstractService<CreateTeamService.TeamArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(TeamArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource team = new Resource(getUniqueId(), arg.name, TYPE_TEAM);
			team.addParameterBag(new li.strolch.model.ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			team.setString(PARAM_NAME, arg.name);
			tx.add(team);
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
