package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;

public class UpdateTeamService extends AbstractService<CreateTeamService.UpdateTeamArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(CreateTeamService.UpdateTeamArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource team = ChronivaroModelHelper.getTeam(tx, arg.id);
			String oldName = team.getName();
			team.setName(arg.name);
			team.setString(PARAM_NAME, arg.name);
			bumpVersion(team, tx);
			tx.update(team);
			ChronivaroAuditHelper.audit(tx, TYPE_TEAM, team.getId(), AUDIT_ACTION_UPDATE, null, PARAM_NAME, oldName,
					arg.name, "Updated team name");
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
