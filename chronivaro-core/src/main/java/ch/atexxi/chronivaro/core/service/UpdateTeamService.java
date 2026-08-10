package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.PARAM_NAME;

public class UpdateTeamService extends AbstractService<CreateTeamService.UpdateTeamArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(CreateTeamService.UpdateTeamArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource team = ChronivaroModelHelper.getTeam(tx, arg.id);
			team.setName(arg.name);
			team.setString(PARAM_NAME, arg.name);
			tx.update(team);
			tx.commitOnClose();
		}
		return ServiceResult.success();
	}

	@Override
	public CreateTeamService.UpdateTeamArgument getArgumentInstance() {
		return new CreateTeamService.UpdateTeamArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
