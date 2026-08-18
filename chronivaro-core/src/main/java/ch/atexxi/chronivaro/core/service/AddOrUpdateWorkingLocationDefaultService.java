package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.WorkingLocation;
import ch.atexxi.chronivaro.core.model.WorkingLocationDurationType;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import java.time.DayOfWeek;
import java.util.Set;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class AddOrUpdateWorkingLocationDefaultService
		extends AbstractService<AddOrUpdateWorkingLocationDefaultService.Argument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(Argument arg) {
		DayOfWeek weekday = requireWeekday(arg.weekday);

		WorkingLocationDurationType durationType = requireDurationType(arg.durationType);
		if (durationType == WorkingLocationDurationType.HALF_DAY && !Set
				.of(DAY_PART_MORNING, DAY_PART_AFTERNOON)
				.contains(arg.dayPart))
			throw new IllegalArgumentException("A half-day default requires MORNING or AFTERNOON");
		if (durationType == WorkingLocationDurationType.FULL_DAY && arg.dayPart != null && !arg.dayPart.isBlank())
			throw new IllegalArgumentException("A full-day default cannot have a day part");
		WorkingLocation.valueOf(arg.workingLocation);
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, arg.employeeId, true);
			Resource existing = tx
					.streamResources(TYPE_WORKING_LOCATION_DEFAULT)
					.filter(r -> arg.id == null || !r.getId().equals(arg.id))
					.filter(r -> arg.employeeId.equals(r.getRelationId(PARAM_EMPLOYEE)))
					.filter(r -> weekday.name().equals(r.getString(PARAM_WEEKDAY)))
					.filter(r -> durationType.name().equals(r.getString(PARAM_DURATION_TYPE)))
					.filter(r -> java.util.Objects.equals(arg.dayPart, r.getString(PARAM_DAY_PART)))
					.findFirst()
					.orElse(null);
			if (existing != null)
				throw new IllegalArgumentException("A default already exists for this weekday and day part");
			Resource resource = arg.id == null ? tx.getResourceTemplate(TYPE_WORKING_LOCATION_DEFAULT, true) :
					tx.getResourceBy(TYPE_WORKING_LOCATION_DEFAULT, arg.id, true);
			resource.setRelation(PARAM_EMPLOYEE, employee);
			resource.setString(PARAM_WEEKDAY, weekday.name());
			resource.setString(PARAM_DURATION_TYPE, durationType.name());
			resource.setString(PARAM_DAY_PART, arg.dayPart == null ? "" : arg.dayPart);
			resource.setString(PARAM_WORKING_LOCATION, arg.workingLocation);
			if (arg.id == null) {
				tx.add(resource);
				ChronivaroAuditHelper.audit(tx, TYPE_WORKING_LOCATION_DEFAULT, resource.getId(), AUDIT_ACTION_CREATE,
						"Added working location default " + weekday.name() + " -> " + arg.workingLocation
								+ " for employee " + arg.employeeId);
			} else {
				tx.update(resource);
				ChronivaroAuditHelper.audit(tx, TYPE_WORKING_LOCATION_DEFAULT, resource.getId(), AUDIT_ACTION_UPDATE,
						"Updated working location default " + weekday.name() + " -> " + arg.workingLocation
								+ " for employee " + arg.employeeId);
			}
			tx.commitOnClose();
		}
		return ServiceResult.success();
	}

	private static DayOfWeek requireWeekday(DayOfWeek weekday) {
		if (weekday == null)
			throw new IllegalArgumentException("Unsupported weekday: null");
		return weekday;
	}

	private static WorkingLocationDurationType requireDurationType(WorkingLocationDurationType durationType) {
		if (durationType == null)
			throw new IllegalArgumentException("Unsupported duration type: null");
		return durationType;
	}

	@Override
	public Argument getArgumentInstance() {
		return new Argument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	public static class Argument extends ServiceArgument {
		public String id, employeeId, dayPart, workingLocation;
		public WorkingLocationDurationType durationType;
		public DayOfWeek weekday;
	}
}
