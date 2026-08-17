package ch.atexxi.chronivaro.core.service;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.TYPE_WORKING_LOCATION_DEFAULT;

public class RemoveWorkingLocationDefaultService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource r = tx.getResourceBy(TYPE_WORKING_LOCATION_DEFAULT, arg.value, true);
			tx.remove(r);
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
