package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import li.strolch.model.Resource;
import li.strolch.model.audit.AccessType;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.handler.PrivilegeHandler;
import li.strolch.privilege.model.Certificate;
import li.strolch.privilege.model.UserRep;
import li.strolch.runtime.sessions.StrolchSessionHandler;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.service.api.ServiceResultState;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.runtime.StrolchConstants.StrolchPrivilegeConstants.*;

public class CompleteRegistrationService
		extends AbstractService<CompleteRegistrationService.CompleteRegistrationArgument, ServiceResult> {

	@Override
	protected ServiceResult getResultInstance() {
		return new ServiceResult(ServiceResultState.FAILED);
	}

	@Override
	public CompleteRegistrationArgument getArgumentInstance() {
		return new CompleteRegistrationArgument();
	}

	@Override
	protected ServiceResult internalDoService(CompleteRegistrationArgument arg) throws Exception {

		StrolchSessionHandler sessionHandler = getComponent(StrolchSessionHandler.class);

		// 1. Validate the challenge
		Certificate challengeCert = sessionHandler.validateChallenge(arg.username, arg.challenge, arg.source);

		try (StrolchTransaction tx = getContainer()
				.getRealm(challengeCert)
				.openTx(challengeCert, PRIVILEGE_SET_USER_PASSWORD, false)) {
			li.strolch.runtime.privilege.PrivilegeHandler strolchPrivilegeHandler
					= getContainer().getPrivilegeHandler();
			PrivilegeHandler privilegeHandler = strolchPrivilegeHandler.getPrivilegeHandler();

			// 2. Set the password
			UserRep userRep = privilegeHandler.setUserPasswordById(challengeCert, challengeCert.getUserId(),
					arg.password.toCharArray());

			tx.add(tx.auditFrom(AccessType.UPDATE, PRIVILEGE, USER, userRep.getUsername()));

			Resource employee = tx
					.streamResources(TYPE_EMPLOYEE)
					.filter(e -> arg.username.equals(e.getString(PARAM_USERNAME)))
					.findFirst()
					.orElse(null);
			String employeeId = employee != null ? employee.getId() : arg.username;
			ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYEE, employeeId, AUDIT_ACTION_REGISTRATION_COMPLETED,
					"Completed registration for user " + arg.username);

			tx.commitOnClose();
		} finally {
			// 3. Invalidate the challenge certificate
			sessionHandler.invalidate(challengeCert);
		}

		return ServiceResult.success();
	}

	public static class CompleteRegistrationArgument extends ServiceArgument {
		public String source;
		public String username;
		public String challenge;
		public String password;
	}
}
