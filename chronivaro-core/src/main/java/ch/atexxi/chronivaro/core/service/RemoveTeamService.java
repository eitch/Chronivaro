package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

public class RemoveTeamService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource team = ChronivaroModelHelper.getTeam(tx, arg.value);
			tx.remove(team);
			tx.commitOnClose();
		}
		return ServiceResult.success();
	}

	@Override
	public li.strolch.service.StringArgument getArgumentInstance() {
		return new li.strolch.service.StringArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
