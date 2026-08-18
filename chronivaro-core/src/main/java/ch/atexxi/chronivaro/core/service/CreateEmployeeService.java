package ch.atexxi.chronivaro.core.service;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.base.PrivilegeConstants;
import li.strolch.privilege.handler.PrivilegeHandler;
import li.strolch.privilege.model.UserRep;
import li.strolch.service.StringResult;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResultState;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.initVersion;
import static li.strolch.privilege.model.UserState.ENABLED;

public class CreateEmployeeService extends AbstractService<CreateEmployeeService.EmployeeArgument, StringResult> {

	@Override
	protected StringResult internalDoService(EmployeeArgument arg) throws Exception {
		String timeZone = arg.timezone == null || arg.timezone.isEmpty() ? getAgent().getTimezone() : arg.timezone;
		ZoneId zoneId = ZoneId.of(timeZone);

		String employeeId;
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {

			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setName(arg.firstname + " " + arg.lastname);

			if (arg.personalNumber == null || arg.personalNumber.isBlank())
				arg.personalNumber = arg.username;

			employee.setRelation(PARAM_PRIMARY_TEAM, tx.getResourceBy(TYPE_TEAM, arg.teamId, true));
			employee.setString(PARAM_PERSONAL_NUMBER, arg.personalNumber);
			employee.setString(PARAM_FIRSTNAME, arg.firstname);
			employee.setString(PARAM_LASTNAME, arg.lastname);
			if (arg.birthdate != null)
				employee.setDate(PARAM_BIRTHDATE, arg.birthdate.atStartOfDay(zoneId));
			else
				employee.removeParameter(PARAM_BIRTHDATE);
			employee.setRelation(PARAM_LOCATION, tx.getResourceBy(TYPE_LOCATION, arg.locationId, true));
			employee.setString(PARAM_TIMEZONE, timeZone);
			employee.setDate(PARAM_JOIN_DATE, arg.joinDate.atStartOfDay(zoneId));
			if (arg.exitDate != null)
				employee.setDate(PARAM_EXIT_DATE, arg.exitDate.atStartOfDay(zoneId));
			employee.setBoolean(PARAM_ACTIVE, arg.active);
			if (arg.email != null && !arg.email.isBlank())
				employee.setString(PARAM_EMAIL, arg.email);
			else
				employee.removeParameter(PARAM_EMAIL);

			UserRep userRep = createOrUpdateUser(tx, arg);
			employee.setString(PARAM_USER_ID, userRep.getUserId());
			employee.setString(PARAM_USERNAME, userRep.getUsername());

			employeeId = employee.getId();
			initVersion(employee, tx);
			tx.add(employee);

			if (arg.scheduleTemplateId != null && !arg.scheduleTemplateId.isBlank()) {
				Resource template = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE_TEMPLATE, arg.scheduleTemplateId, true);
				Resource schedule = tx.getResourceTemplate(TYPE_EMPLOYMENT_SCHEDULE, true);
				schedule.setName("Schedule for " + employee.getName());
				schedule.setRelation(PARAM_EMPLOYEE, employee);
				schedule.setDate(PARAM_VALID_FROM, arg.joinDate.atStartOfDay(zoneId));

				schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY, template.getInteger(PARAM_DAILY_TARGET_MINUTES_MONDAY));
				schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY, template.getInteger(PARAM_DAILY_TARGET_MINUTES_TUESDAY));
				schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY, template.getInteger(PARAM_DAILY_TARGET_MINUTES_WEDNESDAY));
				schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY, template.getInteger(PARAM_DAILY_TARGET_MINUTES_THURSDAY));
				schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY, template.getInteger(PARAM_DAILY_TARGET_MINUTES_FRIDAY));
				schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY, template.getInteger(PARAM_DAILY_TARGET_MINUTES_SATURDAY));
				schedule.setInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY, template.getInteger(PARAM_DAILY_TARGET_MINUTES_SUNDAY));

				initVersion(schedule, tx);
				tx.add(schedule);
				employee.setRelation(PARAM_CURRENT_SCHEDULE, schedule);
			}

			tx.commitOnClose();
		}
		return new StringResult(employeeId);
	}

	static UserRep createOrUpdateUser(StrolchTransaction tx, EmployeeArgument arg) {
		PrivilegeHandler privilegeHandler = tx.getContainer().getPrivilegeHandler().getPrivilegeHandler();
		Map<String, String> properties = new HashMap<>();
		String organisation = tx.getCertificate().getOrganisation();
		if (organisation != null)
			properties.put(PrivilegeConstants.ORGANISATION, organisation);
		if (arg.email != null && !arg.email.isBlank())
			properties.put(PrivilegeConstants.EMAIL, arg.email);
		UserRep userRep = new UserRep(null, arg.username, arg.firstname, arg.lastname, ENABLED, null,
				Set.of(ROLE_EMPLOYEE, ROLE_MODEL_ACCESSOR), tx.getCertificate().getLocale(), properties, null);
		UserRep existingUser = privilegeHandler.getUser(tx.getCertificate(), userRep.getUsername());
		if (existingUser == null) {
			return privilegeHandler.addUser(tx.getCertificate(), userRep, null);
		} else {
			userRep.setUserId(existingUser.getUserId());
			Set<String> roles = new HashSet<>(existingUser.getRoles());
			roles.add(ROLE_EMPLOYEE);
			roles.add(ROLE_MODEL_ACCESSOR);
			userRep.setRoles(roles);
			return privilegeHandler.updateUser(tx.getCertificate(), userRep, null);
		}
	}

	@Override
	public EmployeeArgument getArgumentInstance() {
		return new EmployeeArgument();
	}

	@Override
	public StringResult getResultInstance() {
		return new StringResult(ServiceResultState.FAILED);
	}

	public static class EmployeeArgument extends ServiceArgument {
		public String personalNumber;
		public String firstname;
		public String lastname;
		public LocalDate birthdate;
		public String teamId;
		public String locationId;
		public String timezone;
		public LocalDate joinDate;
		public LocalDate exitDate;
		public boolean active;
		public String username;
		public String email;
		public String scheduleTemplateId;
	}

	public static class UpdateEmployeeArgument extends EmployeeArgument {
		public String id;
	}
}
