package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.handler.PrivilegeHandler;
import li.strolch.privilege.model.UserRep;
import li.strolch.privilege.model.UserState;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class RemoveEmployeeService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource employee = ChronivaroModelHelper.getEmployee(tx, arg.value);

			boolean hasHistoricalBookings = tx.streamResources(TYPE_WORK_DAY)
					.anyMatch(r -> r.hasRelation(PARAM_EMPLOYEE) && r.getRelationId(PARAM_EMPLOYEE).equals(arg.value))
					|| tx.streamResources(TYPE_WORK_ENTRY)
					.anyMatch(r -> r.hasRelation(PARAM_EMPLOYEE) && r.getRelationId(PARAM_EMPLOYEE).equals(arg.value))
					|| tx.streamResources(TYPE_ABSENCE)
					.anyMatch(r -> r.hasRelation(PARAM_EMPLOYEE) && r.getRelationId(PARAM_EMPLOYEE).equals(arg.value))
					|| tx.streamResources(TYPE_TIME_PERIOD)
					.anyMatch(r -> r.hasRelation(PARAM_EMPLOYEE) && r.getRelationId(PARAM_EMPLOYEE).equals(arg.value));

			if (hasHistoricalBookings) {
				return ServiceResult.error("Cannot physically delete employee " + employee.getName()
						+ " because historical bookings exist. Deactivate the employee or user instead.");
			}

			// cascading remove non-booking related data (schedules, initial vacation entries)
			String[] types = {TYPE_VACATION_ACCOUNT_ENTRY, TYPE_EMPLOYMENT_SCHEDULE};

			for (String type : types) {
				tx
						.streamResources(type)
						.filter(r -> r.hasRelation(PARAM_EMPLOYEE) && r
								.getRelationId(PARAM_EMPLOYEE)
								.equals(arg.value))
						.forEach(tx::remove);
			}

			String username = employee.getString(PARAM_USERNAME);
			if (username != null && !username.isBlank()) {
				try {
					PrivilegeHandler privilegeHandler = tx.getContainer().getPrivilegeHandler().getPrivilegeHandler();
					UserRep user = privilegeHandler.getUser(tx.getCertificate(), username);
					if (user != null && user.getUserState() != UserState.SYSTEM) {
						privilegeHandler.removeUser(tx.getCertificate(), user.getUsername());
						ChronivaroAuditHelper.audit(tx, "User", user.getUserId(), AUDIT_ACTION_REMOVE,
								"Removed user " + user.getUsername());
					}
				} catch (Exception ignored) {
				}
			}

			tx.remove(employee);
			ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYEE, employee.getId(), AUDIT_ACTION_REMOVE,
					"Removed employee " + employee.getName());
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
