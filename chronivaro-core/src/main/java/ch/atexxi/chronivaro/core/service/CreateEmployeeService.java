package ch.atexxi.chronivaro.core.service;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.handler.PrivilegeHandler;
import li.strolch.privilege.model.UserRep;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.privilege.model.UserState.ENABLED;

public class CreateEmployeeService extends AbstractService<CreateEmployeeService.EmployeeArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(EmployeeArgument arg) throws Exception {
		String timeZone = arg.timezone == null || arg.timezone.isEmpty() ? getAgent().getTimezone() : arg.timezone;
		ZoneId zoneId = ZoneId.of(timeZone);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {

			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setName(arg.firstname + " " + arg.lastname);

			if (arg.personalNumber == null || arg.personalNumber.isBlank())
				arg.personalNumber = arg.username;

			employee.setRelation(PARAM_PRIMARY_TEAM, tx.getResourceBy(TYPE_TEAM, arg.teamId, true));
			employee.setString(PARAM_PERSONAL_NUMBER, arg.personalNumber);
			employee.setString(PARAM_FIRSTNAME, arg.firstname);
			employee.setString(PARAM_LASTNAME, arg.lastname);
			employee.setDate(PARAM_BIRTHDATE, arg.birthdate.atStartOfDay(zoneId));
			employee.setRelation(PARAM_LOCATION, tx.getResourceBy(TYPE_LOCATION, arg.locationId, true));
			employee.setString(PARAM_TIMEZONE, timeZone);
			employee.setDate(PARAM_JOIN_DATE, arg.joinDate.atStartOfDay(zoneId));
			if (arg.exitDate != null)
				employee.setDate(PARAM_EXIT_DATE, arg.exitDate.atStartOfDay(zoneId));
			employee.setBoolean(PARAM_ACTIVE, arg.active);

			UserRep userRep = createUser(tx, arg);
			employee.setRelationId(PARAM_USER, userRep.getUserId());

			tx.add(employee);
			tx.commitOnClose();
		}
		return ServiceResult.success();
	}

	private UserRep createUser(StrolchTransaction tx, EmployeeArgument arg) {
		PrivilegeHandler privilegeHandler = getContainer().getPrivilegeHandler().getPrivilegeHandler();
		UserRep userRep = new UserRep(null, arg.username, arg.firstname, arg.lastname, ENABLED, null,
				Set.of(ROLE_EMPLOYEE), tx.getCertificate().getLocale(), null, null);
		return privilegeHandler.addUser(tx.getCertificate(), userRep, null);
	}

	@Override
	public EmployeeArgument getArgumentInstance() {
		return new EmployeeArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
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
	}

	public static class UpdateEmployeeArgument extends EmployeeArgument {
		public String id;
	}
}
