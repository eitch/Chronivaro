package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.handler.PrivilegeHandler;
import li.strolch.privilege.model.Usage;
import li.strolch.privilege.model.UserRep;
import li.strolch.privilege.model.UserState;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.AUDIT_ACTION_REGISTRATION_INITIATED;

public class InitiateUserRegistrationService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("User identifier must not be empty", arg.value);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			PrivilegeHandler privilegeHandler = tx.getContainer().getPrivilegeHandler().getPrivilegeHandler();

			UserRep user = privilegeHandler.getUser(tx.getCertificate(), arg.value);
			if (user == null) {
				List<UserRep> allUsers = privilegeHandler.getUsers(tx.getCertificate());
				for (UserRep u : allUsers) {
					if (arg.value.equals(u.getUserId()) || arg.value.equalsIgnoreCase(u.getUsername())) {
						user = u;
						break;
					}
				}
			}

			if (user == null || user.getUserState() == UserState.SYSTEM) {
				return ServiceResult.error("User " + arg.value + " not found!");
			}

			privilegeHandler.initiateChallengeFor(Usage.SET_PASSWORD, user.getUsername(), "unknown");

			ChronivaroAuditHelper.audit(tx, "User", user.getUserId(), AUDIT_ACTION_REGISTRATION_INITIATED,
					"Initiated registration challenge for user " + user.getUsername());

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
