package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;

public class UpdateAbsenceTypeService
		extends AbstractService<CreateAbsenceTypeService.UpdateAbsenceTypeArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(CreateAbsenceTypeService.UpdateAbsenceTypeArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource type = ChronivaroModelHelper.getAbsenceType(tx, arg.id);
			type.setName(arg.name);
			type.setString(PARAM_CODE, arg.code);
			type.setString(PARAM_NAME, arg.name);
			type.setBoolean(PARAM_COUNT_AS_TARGET_TIME, arg.countAsTargetTime);
			type.setBoolean(PARAM_REDUCE_VACATION_CREDIT, arg.reduceVacationCredit);
			type.setBoolean(PARAM_PAID, arg.paid);
			type.setBoolean(PARAM_APPROVAL_REQUIRED, arg.approvalRequired);
			type.setStringList(PARAM_DURATION_TYPES, arg.durationTypes);
			type.setBoolean(PARAM_ACTIVE, arg.active);
			bumpVersion(type, tx);
			tx.update(type);
			tx.commitOnClose();
		}
		return ServiceResult.success();
	}

	@Override
	public CreateAbsenceTypeService.UpdateAbsenceTypeArgument getArgumentInstance() {
		return new CreateAbsenceTypeService.UpdateAbsenceTypeArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
