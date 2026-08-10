package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import java.time.ZoneId;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class UpdateEmployeeService
		extends AbstractService<CreateEmployeeService.UpdateEmployeeArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(CreateEmployeeService.UpdateEmployeeArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource employee = ChronivaroModelHelper.getEmployee(tx, arg.id);
			ZoneId zoneId = ChronivaroModelHelper.getEmployeeTimezone(employee);

			employee.setName(arg.displayName);
			employee.setString(PARAM_PERSONAL_NUMBER, arg.personalNumber);
			employee.setString(PARAM_DISPLAY_NAME, arg.displayName);
			employee.setString(BAG_RELATIONS, TYPE_TEAM, arg.teamId);
			employee.setString(BAG_RELATIONS, TYPE_LOCATION, arg.locationId);
			employee.setString(PARAM_TIMEZONE, arg.timezone);
			employee.setDate(PARAM_JOIN_DATE, arg.joinDate.atStartOfDay(zoneId));
			if (arg.exitDate != null)
				employee.setDate(PARAM_EXIT_DATE, arg.exitDate.atStartOfDay(zoneId));
			else
				employee.removeParameter(PARAM_EXIT_DATE);
			employee.setBoolean(PARAM_ACTIVE, arg.active);
			employee.setString(BAG_RELATIONS, PARAM_USER, arg.userId);
			tx.update(employee);
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
