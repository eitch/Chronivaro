package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.model.StrolchModelConstants.BAG_RELATIONS;

public class RemoveLocationService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource location = ChronivaroModelHelper.getLocation(tx, arg.value);

			boolean employeeReferencing = tx
					.streamResources(TYPE_EMPLOYEE)
					.anyMatch(e -> e.hasParameter(BAG_RELATIONS, PARAM_LOCATION) && e
							.getString(BAG_RELATIONS, PARAM_LOCATION)
							.equals(arg.value));

			if (employeeReferencing) {
				return ServiceResult.error("Location is still referenced by an employee!");
			}

			tx.remove(location);
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
