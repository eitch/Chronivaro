package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.handler.PrivilegeHandler;
import li.strolch.privilege.model.Usage;
import li.strolch.privilege.model.UserRep;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class InitiateEmployeeRegistrationService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {

			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, arg.value, true);

			String username = employee.getString(PARAM_USERNAME);
			if (username == null || username.isBlank())
				return ServiceResult.error("Employee has no linked user!");

			PrivilegeHandler privilegeHandler = tx.getContainer().getPrivilegeHandler().getPrivilegeHandler();
			UserRep user = privilegeHandler.getUser(tx.getCertificate(), username);
			if (user == null)
				return ServiceResult.error("Linked user " + username + " not found in Strolch!");

			privilegeHandler.initiateChallengeFor(Usage.SET_PASSWORD, username, "unknown");

			ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYEE, employee.getId(), "registrationInitiated", null, username);

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
