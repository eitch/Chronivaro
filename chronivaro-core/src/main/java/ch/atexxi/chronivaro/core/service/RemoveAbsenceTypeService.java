package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.PARAM_ABSENCE_TYPE;
import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.TYPE_ABSENCE;
import static li.strolch.model.StrolchModelConstants.BAG_RELATIONS;

public class RemoveAbsenceTypeService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource type = ChronivaroModelHelper.getAbsenceType(tx, arg.value);

			boolean absenceReferencing = tx
					.streamResources(TYPE_ABSENCE)
					.anyMatch(a -> a.hasParameter(BAG_RELATIONS, PARAM_ABSENCE_TYPE) && a
							.getString(BAG_RELATIONS, PARAM_ABSENCE_TYPE)
							.equals(arg.value));

			if (absenceReferencing) {
				return ServiceResult.error("Absence type is still referenced by an absence!");
			}

			tx.remove(type);
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
