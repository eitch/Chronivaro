package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.AUDIT_ACTION_REMOVE;
import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE;

public class RemoveScheduleTemplateService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource template = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE, arg.value, true);
			tx.remove(template);
			ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE, template.getId(), AUDIT_ACTION_REMOVE,
					"Removed schedule template " + template.getName());
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
