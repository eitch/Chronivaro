package ch.atexxi.chronivaro.core.service;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.ServiceResult;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class CreateTeamService extends AbstractService<CreateTeamService.TeamArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(TeamArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource team = new Resource(arg.id, arg.name, TYPE_TEAM);
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
		public String id;
		public String name;
	}
}
