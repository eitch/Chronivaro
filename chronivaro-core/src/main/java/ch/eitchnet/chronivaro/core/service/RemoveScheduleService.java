package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.VacationHelper;
import li.strolch.exception.StrolchUserMessageException;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.I18nMessage;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.TreeSet;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;

public class RemoveScheduleService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource schedule = tx.getResourceBy(TYPE_EMPLOYMENT_SCHEDULE, arg.value, true);

			String employeeId = schedule.getRelationId(PARAM_EMPLOYEE);
			ZonedDateTime validFrom = schedule.getDate(PARAM_VALID_FROM);
			ZonedDateTime validTo = schedule.getDate(PARAM_VALID_TO);

			long workEntries = tx.streamResources(TYPE_WORK_ENTRY)
					.filter(e -> e.getRelationId(PARAM_EMPLOYEE).equals(employeeId))
					.filter(e -> {
						ZonedDateTime start = e.getDate(PARAM_START);
						if (start.isBefore(validFrom))
							return false;
						return validTo == null || !start.isAfter(validTo);
					})
					.count();

			if (workEntries > 0) {
				throw new StrolchUserMessageException(
						new I18nMessage("chronivaro", "chronivaro.schedule.delete.fail.workentries", null,
								"Cannot delete schedule because it has " + workEntries
										+ " work entries associated with it. Please update the schedule instead.")
								.value("count", workEntries));
			}

			tx.remove(schedule);
			ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYMENT_SCHEDULE, schedule.getId(), AUDIT_ACTION_REMOVE,
					"Removed schedule " + schedule.getId() + " for employee " + employeeId);

			updateEmployeeCurrentSchedule(tx, employeeId);

			Set<Integer> years = new TreeSet<>();
			years.add(validFrom.getYear());
			if (validTo != null) {
				years.add(validTo.getYear());
			}
			tx.streamResources(TYPE_VACATION_ACCOUNT_ENTRY)
					.filter(e -> e.hasRelation(PARAM_EMPLOYEE) && employeeId.equals(e.getRelationId(PARAM_EMPLOYEE))
							&& VACATION_ENTITLEMENT.equals(e.getString(PARAM_VACATION_TYPE)))
					.map(e -> e.getDate(PARAM_DATE).getYear())
					.forEach(years::add);

			for (int year : years) {
				VacationHelper.creditOrRecalculateEntitlement(tx, employeeId, year, true,
						"removal of schedule version (validFrom " + validFrom.toLocalDate() + ")");
			}

			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	private void updateEmployeeCurrentSchedule(StrolchTransaction tx, String employeeId) {
		ZonedDateTime now = ZonedDateTime.now();
		Resource schedule = tx
				.streamResources(TYPE_EMPLOYMENT_SCHEDULE)
				.filter(s -> s.hasRelation(PARAM_EMPLOYEE) && employeeId.equals(s.getRelationId(PARAM_EMPLOYEE)))
				.filter(s -> {
					ZonedDateTime validFrom = s.getDate(PARAM_VALID_FROM);
					ZonedDateTime validTo = s.hasParameter(PARAM_VALID_TO) ? s.getDate(PARAM_VALID_TO) : null;
					return !now.isBefore(validFrom) && (validTo == null || !now.isAfter(validTo));
				})
				.findFirst()
				.orElse(null);

		Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, true);
		if (schedule == null) {
			employee.removeParameter(BAG_RELATIONS, PARAM_CURRENT_SCHEDULE);
		} else {
			employee.setRelation(PARAM_CURRENT_SCHEDULE, schedule);
		}
		tx.update(employee);
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
