package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.VacationHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.base.PrivilegeConstants;
import li.strolch.privilege.handler.PrivilegeHandler;
import li.strolch.privilege.model.UserRep;
import li.strolch.privilege.model.UserState;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.privilege.model.UserState.ENABLED;

public class ReactivateEmployeeService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("Employee identifier must not be empty", arg.value);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource employee = ChronivaroModelHelper.getEmployee(tx, arg.value);
			employee = tx.readLock(employee);

			if (employee.getBoolean(PARAM_ACTIVE)) {
				return ServiceResult.error("Employee " + employee.getName() + " is already active!");
			}

			employee.setBoolean(PARAM_ACTIVE, true);

			String username = employee.getString(PARAM_USERNAME);
			if (username == null || username.isBlank()) {
				username = employee.getString(PARAM_PERSONAL_NUMBER);
				employee.setString(PARAM_USERNAME, username);
			}

			String firstname = employee.getString(PARAM_FIRSTNAME);
			String lastname = employee.getString(PARAM_LASTNAME);
			String email = employee.hasParameter(PARAM_EMAIL) ? employee.getString(PARAM_EMAIL) : null;

			PrivilegeHandler privilegeHandler = tx.getContainer().getPrivilegeHandler().getPrivilegeHandler();
			Map<String, String> properties = new HashMap<>();
			String organisation = tx.getCertificate().getOrganisation();
			if (organisation != null)
				properties.put(PrivilegeConstants.ORGANISATION, organisation);
			if (email != null && !email.isBlank())
				properties.put(PrivilegeConstants.EMAIL, email);

			UserRep userRep = privilegeHandler.getUser(tx.getCertificate(), username);
			if (userRep == null) {
				userRep = new UserRep(null, username, firstname, lastname, ENABLED, null,
						Set.of(ROLE_EMPLOYEE, ROLE_MODEL_ACCESSOR), tx.getCertificate().getLocale(), properties, null);
				userRep = privilegeHandler.addUser(tx.getCertificate(), userRep, null);
			} else {
				if (userRep.getUserState() == UserState.DISABLED) {
					userRep.setUserState(ENABLED);
				}
				userRep.setFirstname(firstname);
				userRep.setLastname(lastname);
				Set<String> roles = new HashSet<>(userRep.getRoles());
				roles.add(ROLE_EMPLOYEE);
				roles.add(ROLE_MODEL_ACCESSOR);
				userRep.setRoles(roles);
				if (email != null && !email.isBlank())
					userRep.setProperty(PrivilegeConstants.EMAIL, email);
				userRep = privilegeHandler.updateUser(tx.getCertificate(), userRep, null);
			}

			employee.setString(PARAM_USER_ID, userRep.getUserId());
			employee.setString(PARAM_USERNAME, userRep.getUsername());

			ZoneId zoneId = ChronivaroModelHelper.getEmployeeTimezone(employee);
			LocalDate now = LocalDate.now(zoneId);
			if (employee.hasParameter(PARAM_EXIT_DATE)) {
				LocalDate exitDate = ChronivaroModelHelper.getExitDate(employee).orElse(null);
				if (exitDate != null && exitDate.isBefore(now)) {
					employee.removeParameter(PARAM_EXIT_DATE);
				}
			}

			tx.update(employee);

			// Initialize or verify vacation account entitlement for the current year
			VacationHelper.creditOrRecalculateEntitlement(tx, employee.getId(), now.getYear(), false);

			ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYEE, employee.getId(), AUDIT_ACTION_REACTIVATE,
					"Reactivated employee " + employee.getName());
			ChronivaroAuditHelper.audit(tx, "User", userRep.getUserId(), AUDIT_ACTION_CREATE,
					"Created/Restored user account " + userRep.getUsername() + " for reactivated employee "
							+ employee.getName());

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
