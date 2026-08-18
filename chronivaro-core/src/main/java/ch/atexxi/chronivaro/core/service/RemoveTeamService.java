package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class RemoveTeamService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource team = ChronivaroModelHelper.getTeam(tx, arg.value);

			boolean employeeReferencing = tx
					.streamResources(TYPE_EMPLOYEE)
					.anyMatch(e -> e.hasRelation(PARAM_PRIMARY_TEAM) && e.getRelationId(PARAM_PRIMARY_TEAM).equals(arg.value));

			if (employeeReferencing) {
				return ServiceResult.error("Team is still referenced by an employee!");
			}

			tx.remove(team);
			ChronivaroAuditHelper.audit(tx, TYPE_TEAM, team.getId(), AUDIT_ACTION_REMOVE, "Removed team " + team.getName());
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
