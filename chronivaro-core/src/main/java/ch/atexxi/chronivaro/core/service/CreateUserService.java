package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.base.PrivilegeConstants;
import li.strolch.privilege.handler.PrivilegeHandler;
import li.strolch.privilege.model.UserRep;
import li.strolch.privilege.model.UserState;
import li.strolch.service.StringResult;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResultState;
import li.strolch.utils.dbc.DBC;

import java.util.*;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.privilege.model.UserState.ENABLED;

public class CreateUserService extends AbstractService<CreateUserService.UserArgument, StringResult> {

	@Override
	protected StringResult internalDoService(UserArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("Username must not be empty", arg.username);
		DBC.PRE.assertNotEmpty("Firstname must not be empty", arg.firstname);
		DBC.PRE.assertNotEmpty("Lastname must not be empty", arg.lastname);
		DBC.PRE.assertNotNull("Roles must not be null", arg.roles);
		DBC.PRE.assertFalse("At least one role must be specified", arg.roles.isEmpty());
		if (arg.state == UserState.SYSTEM) {
			StringResult result = new StringResult(ServiceResultState.FAILED);
			result.setMessage("Cannot create user with SYSTEM state!");
			return result;
		}

		String userId;
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			PrivilegeHandler privilegeHandler = tx.getContainer().getPrivilegeHandler().getPrivilegeHandler();

			UserRep existingUser = privilegeHandler.getUser(tx.getCertificate(), arg.username);
			if (existingUser != null) {
				StringResult result = new StringResult(ServiceResultState.FAILED);
				result.setMessage("User with username " + arg.username + " already exists!");
				return result;
			}

			Map<String, String> properties = new HashMap<>();
			String organisation = tx.getCertificate().getOrganisation();
			if (organisation != null)
				properties.put(PrivilegeConstants.ORGANISATION, organisation);
			if (arg.email != null && !arg.email.isBlank())
				properties.put(PrivilegeConstants.EMAIL, arg.email.trim());

			Set<String> roles = new HashSet<>(arg.roles);
			roles.add(ROLE_MODEL_ACCESSOR);

			Locale locale = tx.getCertificate().getLocale();
			if (arg.locale != null && !arg.locale.isBlank()) {
				locale = Locale.forLanguageTag(arg.locale);
			}

			UserState state = arg.state != null ? arg.state : ENABLED;

			UserRep userRep = new UserRep(null, arg.username.trim(), arg.firstname.trim(), arg.lastname.trim(),
					state, null, roles, locale, properties, null);

			UserRep createdUser = privilegeHandler.addUser(tx.getCertificate(), userRep, null);
			userId = createdUser.getUserId();

			ChronivaroAuditHelper.audit(tx, "User", userId, AUDIT_ACTION_CREATE,
					"Created user " + createdUser.getUsername() + " (" + createdUser.getFirstname() + " "
							+ createdUser.getLastname() + ")");

			tx.commitOnClose();
		}

		return new StringResult(userId);
	}

	@Override
	public UserArgument getArgumentInstance() {
		return new UserArgument();
	}

	@Override
	public StringResult getResultInstance() {
		return new StringResult(ServiceResultState.FAILED);
	}

	public static class UserArgument extends ServiceArgument {
		public String username;
		public String firstname;
		public String lastname;
		public String email;
		public Set<String> roles = new HashSet<>();
		public UserState state = ENABLED;
		public String locale;
	}
}
