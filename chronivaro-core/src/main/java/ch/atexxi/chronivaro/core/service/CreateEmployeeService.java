package ch.atexxi.chronivaro.core.service;

import li.strolch.model.ParameterBag;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import java.time.LocalDate;
import java.time.ZoneId;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.agent.api.StrolchAgent.getUniqueId;

public class CreateEmployeeService extends AbstractService<CreateEmployeeService.EmployeeArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(EmployeeArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			ZoneId zoneId = arg.timezone == null || arg.timezone.isEmpty() ? ZoneId.of("Europe/Zurich") :
					ZoneId.of(arg.timezone);

			Resource employee = new Resource(getUniqueId(), arg.displayName, TYPE_EMPLOYEE);
			employee.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			employee.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));
			employee.setString(PARAM_PERSONAL_NUMBER, arg.personalNumber);
			employee.setString(PARAM_DISPLAY_NAME, arg.displayName);
			employee.setString(BAG_RELATIONS, TYPE_TEAM, arg.teamId);
			employee.setString(BAG_RELATIONS, TYPE_LOCATION, arg.locationId);
			employee.setString(PARAM_TIMEZONE, arg.timezone);
			employee.setDate(PARAM_JOIN_DATE, arg.joinDate.atStartOfDay(zoneId));
			if (arg.exitDate != null)
				employee.setDate(PARAM_EXIT_DATE, arg.exitDate.atStartOfDay(zoneId));
			employee.setBoolean(PARAM_ACTIVE, arg.active);
			employee.setString(BAG_RELATIONS, PARAM_USER, arg.userId);
			tx.add(employee);
			tx.commitOnClose();
		}
		return ServiceResult.success();
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
		public String displayName;
		public String teamId;
		public String locationId;
		public String timezone;
		public LocalDate joinDate;
		public LocalDate exitDate;
		public boolean active;
		public String userId;
	}

	public static class UpdateEmployeeArgument extends EmployeeArgument {
		public String id;
	}
}
