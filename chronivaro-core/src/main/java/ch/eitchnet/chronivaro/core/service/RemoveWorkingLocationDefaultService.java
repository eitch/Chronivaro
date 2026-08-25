package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.AUDIT_ACTION_REMOVE;
import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.TYPE_WORKING_LOCATION_DEFAULT;

public class RemoveWorkingLocationDefaultService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource r = tx.getResourceBy(TYPE_WORKING_LOCATION_DEFAULT, arg.value, true);
			tx.remove(r);
			ChronivaroAuditHelper.audit(tx, TYPE_WORKING_LOCATION_DEFAULT, r.getId(), AUDIT_ACTION_REMOVE,
					"Removed working location default " + r.getId());
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
