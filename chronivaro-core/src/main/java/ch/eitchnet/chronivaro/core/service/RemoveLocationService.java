package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;

public class RemoveLocationService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource location = ChronivaroModelHelper.getLocation(tx, arg.value);

			boolean employeeReferencing = tx
					.streamResources(TYPE_EMPLOYEE)
					.anyMatch(e -> e.hasRelation(PARAM_LOCATION) && e.getRelationId(PARAM_LOCATION).equals(arg.value));

			if (employeeReferencing) {
				return ServiceResult.error("Location is still referenced by an employee!");
			}

			tx.remove(location);
			ChronivaroAuditHelper.audit(tx, TYPE_LOCATION, location.getId(), AUDIT_ACTION_REMOVE,
					"Removed location " + location.getName());
			tx.commitOnClose();
		}
		return ServiceResult.success();
	}

	@Override
	public StringArgument getArgumentInstance() {
		return new StringArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
