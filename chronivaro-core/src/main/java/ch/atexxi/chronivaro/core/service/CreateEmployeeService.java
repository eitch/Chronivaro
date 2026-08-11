package ch.atexxi.chronivaro.core.service;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import java.time.LocalDate;
import java.time.ZoneId;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class CreateEmployeeService extends AbstractService<CreateEmployeeService.EmployeeArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(EmployeeArgument arg) throws Exception {
		String timeZone = arg.timezone == null || arg.timezone.isEmpty() ? getAgent().getTimezone() : arg.timezone;
		ZoneId zoneId = ZoneId.of(timeZone);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {

			Resource employee = tx.getResourceTemplate(TYPE_EMPLOYEE, true);
			employee.setName(arg.displayName);

			employee.setRelation(PARAM_PRIMARY_TEAM, tx.getResourceBy(TYPE_TEAM, arg.teamId, true));
			employee.setRelationId(PARAM_USER, arg.userId);
			employee.setString(PARAM_PERSONAL_NUMBER, arg.personalNumber);
			employee.setString(PARAM_DISPLAY_NAME, arg.displayName);
			employee.setRelation(PARAM_LOCATION, tx.getResourceBy(TYPE_LOCATION, arg.locationId, true));
			employee.setString(PARAM_TIMEZONE, timeZone);
			employee.setDate(PARAM_JOIN_DATE, arg.joinDate.atStartOfDay(zoneId));
			if (arg.exitDate != null)
				employee.setDate(PARAM_EXIT_DATE, arg.exitDate.atStartOfDay(zoneId));
			employee.setBoolean(PARAM_ACTIVE, arg.active);
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
