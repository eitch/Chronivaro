package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.initVersion;

public class CreateAbsenceTypeService
		extends AbstractService<CreateAbsenceTypeService.AbsenceTypeArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(AbsenceTypeArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource type = tx.getResourceTemplate(TYPE_ABSENCE_TYPE, true);
			type.setName(arg.name);
			type.setString(PARAM_CODE, arg.code);
			type.setString(PARAM_NAME, arg.name);
			type.setBoolean(PARAM_COUNT_AS_TARGET_TIME, arg.countAsTargetTime);
			type.setBoolean(PARAM_REDUCE_VACATION_CREDIT, arg.reduceVacationCredit);
			type.setBoolean(PARAM_PAID, arg.paid);
			type.setBoolean(PARAM_APPROVAL_REQUIRED, arg.approvalRequired);
			if (arg.durationTypes != null)
				type.setStringList(PARAM_DURATION_TYPES, arg.durationTypes);
			type.setBoolean(PARAM_ACTIVE, arg.active);
			initVersion(type, tx);
			tx.add(type);
			ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE_TYPE, type.getId(), AUDIT_ACTION_CREATE,
					"Created absence type " + arg.name + " (" + arg.code + ")");
			tx.commitOnClose();
		}
		return ServiceResult.success();
	}

	@Override
	public AbsenceTypeArgument getArgumentInstance() {
		return new AbsenceTypeArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	public static class AbsenceTypeArgument extends ServiceArgument {
		public String code;
		public String name;
		public boolean countAsTargetTime;
		public boolean reduceVacationCredit;
		public boolean paid;
		public boolean approvalRequired;
		public List<String> durationTypes;
		public boolean active;
	}

	public static class UpdateAbsenceTypeArgument extends AbsenceTypeArgument {
		public String id;
	}
}
