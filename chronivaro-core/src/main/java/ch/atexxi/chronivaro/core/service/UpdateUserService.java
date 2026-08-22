package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.base.PrivilegeConstants;
import li.strolch.privilege.handler.PrivilegeHandler;
import li.strolch.privilege.model.UserRep;
import li.strolch.privilege.model.UserState;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.util.*;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class UpdateUserService extends AbstractService<UpdateUserService.UpdateUserArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(UpdateUserArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("User identifier must not be empty", arg.userId);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			PrivilegeHandler privilegeHandler = tx.getContainer().getPrivilegeHandler().getPrivilegeHandler();

			UserRep user = privilegeHandler.getUser(tx.getCertificate(), arg.userId);
			if (user == null) {
				// Try finding by userId among all users
				List<UserRep> allUsers = privilegeHandler.getUsers(tx.getCertificate());
				for (UserRep u : allUsers) {
					if (arg.userId.equals(u.getUserId()) || arg.userId.equalsIgnoreCase(u.getUsername())) {
						user = u;
						break;
					}
				}
			}

			if (user == null) {
				return ServiceResult.error("User " + arg.userId + " not found!");
			}

			if (arg.firstname != null && !arg.firstname.isBlank()) {
				user.setFirstname(arg.firstname.trim());
			}
			if (arg.lastname != null && !arg.lastname.isBlank()) {
				user.setLastname(arg.lastname.trim());
			}
			if (arg.state != null) {
				user.setUserState(arg.state);
			}
			if (arg.locale != null && !arg.locale.isBlank()) {
				user.setLocale(Locale.forLanguageTag(arg.locale.trim()));
			}

			if (arg.roles != null && !arg.roles.isEmpty()) {
				Set<String> roles = new HashSet<>(arg.roles);
				roles.add(ROLE_MODEL_ACCESSOR);
				user.setRoles(roles);
			}

			Map<String, String> properties = new HashMap<>(user.getProperties());
			if (arg.email != null) {
				if (arg.email.isBlank()) {
					properties.remove(PrivilegeConstants.EMAIL);
				} else {
					properties.put(PrivilegeConstants.EMAIL, arg.email.trim());
				}
			}
			user.setProperties(properties);

			UserRep updatedUser = privilegeHandler.updateUser(tx.getCertificate(), user, null);

			ChronivaroAuditHelper.audit(tx, "User", updatedUser.getUserId(), AUDIT_ACTION_UPDATE,
					"Updated user " + updatedUser.getUsername() + " (" + updatedUser.getFirstname() + " "
							+ updatedUser.getLastname() + ")");

			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	@Override
	public UpdateUserArgument getArgumentInstance() {
		return new UpdateUserArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	public static class UpdateUserArgument extends ServiceArgument {
		public String userId;
		public String firstname;
		public String lastname;
		public String email;
		public Set<String> roles;
		public UserState state;
		public String locale;
	}
}
