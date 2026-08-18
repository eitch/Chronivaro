package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.UserRep;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import java.time.ZoneId;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static ch.atexxi.chronivaro.core.model.ChronivaroVersionHelper.bumpVersion;
import static ch.atexxi.chronivaro.core.service.CreateEmployeeService.createOrUpdateUser;

public class UpdateEmployeeService
		extends AbstractService<CreateEmployeeService.UpdateEmployeeArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(CreateEmployeeService.UpdateEmployeeArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource employee = ChronivaroModelHelper.getEmployee(tx, arg.id);
			ZoneId zoneId = ChronivaroModelHelper.getEmployeeTimezone(employee);

			employee.setName(arg.firstname + " " + arg.lastname);
			employee.setString(PARAM_PERSONAL_NUMBER, arg.personalNumber);
			employee.setString(PARAM_FIRSTNAME, arg.firstname);
			employee.setString(PARAM_LASTNAME, arg.lastname);
			if (arg.birthdate != null)
				employee.setDate(PARAM_BIRTHDATE, arg.birthdate.atStartOfDay(zoneId));
			else
				employee.removeParameter(PARAM_BIRTHDATE);
			employee.setRelation(PARAM_PRIMARY_TEAM, tx.getResourceBy(TYPE_TEAM, arg.teamId, true));
			employee.setRelation(PARAM_LOCATION, tx.getResourceBy(TYPE_LOCATION, arg.locationId, true));
			employee.setString(PARAM_TIMEZONE, arg.timezone);
			employee.setDate(PARAM_JOIN_DATE, arg.joinDate.atStartOfDay(zoneId));
			if (arg.exitDate != null)
				employee.setDate(PARAM_EXIT_DATE, arg.exitDate.atStartOfDay(zoneId));
			else
				employee.removeParameter(PARAM_EXIT_DATE);
			employee.setBoolean(PARAM_ACTIVE, arg.active);
			if (arg.email != null && !arg.email.isBlank())
				employee.setString(PARAM_EMAIL, arg.email);
			else
				employee.removeParameter(PARAM_EMAIL);
			bumpVersion(employee, tx);
			tx.update(employee);

			UserRep userRep = createOrUpdateUser(tx, arg);
			employee.setString(PARAM_USER_ID, userRep.getUserId());
			employee.setString(PARAM_USERNAME, userRep.getUsername());

			tx.commitOnClose();
		}
		return ServiceResult.success();
	}

	@Override
	public CreateEmployeeService.UpdateEmployeeArgument getArgumentInstance() {
		return new CreateEmployeeService.UpdateEmployeeArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
