package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.handler.PrivilegeHandler;
import li.strolch.privilege.model.UserRep;
import li.strolch.privilege.model.UserState;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class RemoveUserService extends AbstractService<StringArgument, ServiceResult> {

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

			// Check for linked employee
			Resource linkedEmployee = null;
			for (Resource emp : tx.streamResources(TYPE_EMPLOYEE).toList()) {
				String uName = emp.getString(PARAM_USERNAME);
				String uId = emp.getString(PARAM_USER_ID);
				if ((uName != null && uName.equalsIgnoreCase(user.getUsername())) || (uId != null && uId.equals(user.getUserId()))) {
					linkedEmployee = emp;
					break;
				}
			}

			if (linkedEmployee != null) {
				linkedEmployee = tx.readLock(linkedEmployee);
				linkedEmployee.setBoolean(PARAM_ACTIVE, false);
				tx.update(linkedEmployee);
				ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYEE, linkedEmployee.getId(), AUDIT_ACTION_DEACTIVATE,
						"Deactivated employee " + linkedEmployee.getName() + " due to user deletion");
			}

			privilegeHandler.removeUser(tx.getCertificate(), user.getUsername());

			ChronivaroAuditHelper.audit(tx, "User", user.getUserId(), AUDIT_ACTION_REMOVE,
					"Removed user " + user.getUsername());

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
