package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroModelHelper;
import ch.atexxi.chronivaro.core.model.WorkEntryHelper;
import li.strolch.model.ParameterBag;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;
import java.util.UUID;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class StartTimerService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.value);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource employee = ChronivaroModelHelper.getEmployee(tx, arg.value);

			if (WorkEntryHelper.findActiveWorkEntry(tx, arg.value).isPresent()) {
				throw new IllegalStateException("An active work entry already exists for this employee!");
			}

			ZonedDateTime now = ZonedDateTime.now(ChronivaroModelHelper.getEmployeeTimezone(employee));

			Resource workEntry = new Resource(UUID.randomUUID().toString(), "Timer " + now, TYPE_WORK_ENTRY);
			workEntry.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			workEntry.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));

			workEntry.setString(BAG_RELATIONS, TYPE_EMPLOYEE, employee.getId());
			workEntry.setDate(PARAM_START, now);
			workEntry.setString(PARAM_SOURCE, SOURCE_TIMER);
			workEntry.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());

			WorkEntryHelper.validateNoOverlap(tx, employee.getId(), now, null, null);

			tx.add(workEntry);
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
