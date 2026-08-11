package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.model.StrolchModelConstants.BAG_RELATIONS;

public class RemoveEmployeeService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource employee = ChronivaroModelHelper.getEmployee(tx, arg.value);

			// cascading remove related data
			String[] types = {TYPE_WORK_ENTRY, TYPE_ABSENCE, TYPE_VACATION_ACCOUNT_ENTRY, TYPE_TIME_PERIOD,
					TYPE_EMPLOYMENT_SCHEDULE_VERSION};

			for (String type : types) {
				tx
						.streamResources(type)
						.filter(r -> r.hasRelation(PARAM_EMPLOYEE) && r
								.getRelationId(PARAM_EMPLOYEE)
								.equals(arg.value))
						.forEach(tx::remove);
			}

			tx.remove(employee);
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
