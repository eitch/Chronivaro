package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static ch.eitchnet.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;

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
			type.setBoolean(PARAM_COMMENT_REQUIRED, arg.commentRequired);
			type.setBoolean(PARAM_VISIBLE_ON_PUBLIC_STATUS, arg.visibleOnPublicStatus);
			type.setStringList(PARAM_DURATION_TYPES, arg.durationTypes);
			type.setBoolean(PARAM_ACTIVE, arg.active);
			bumpVersion(type, tx);
			tx.update(type);
			ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE_TYPE, type.getId(), AUDIT_ACTION_UPDATE,
					"Updated absence type " + arg.name + " (" + arg.code + ")");
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
