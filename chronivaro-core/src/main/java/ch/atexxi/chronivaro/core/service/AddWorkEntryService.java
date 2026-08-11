package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.WorkEntryHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class AddWorkEntryService extends AbstractService<AddWorkEntryService.AddWorkEntryArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(AddWorkEntryArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.employeeId);
		DBC.PRE.assertNotNull("start must be set", arg.start);
		DBC.PRE.assertNotNull("end must be set", arg.end);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			if (arg.end.isBefore(arg.start)) {
				throw new IllegalStateException("End time cannot be before start time!");
			}

			WorkEntryHelper.validateNoOverlap(tx, arg.employeeId, arg.start, arg.end, null);

			Resource workEntry = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
			workEntry.setName("WorkEntry " + arg.start);

			workEntry.setRelation(PARAM_EMPLOYEE, tx.getResourceBy(TYPE_EMPLOYEE, arg.employeeId, true));
			workEntry.setDate(PARAM_START, arg.start);
			workEntry.setDate(PARAM_END, arg.end);
			workEntry.setString(PARAM_SOURCE, SOURCE_MANUAL);
			workEntry.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());
			if (arg.comment != null)
				workEntry.setString(PARAM_COMMENT, arg.comment);

			tx.add(workEntry);
			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	@Override
	public AddWorkEntryArgument getArgumentInstance() {
		return new AddWorkEntryArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	public static class AddWorkEntryArgument extends ServiceArgument {
		public String employeeId;
		public ZonedDateTime start;
		public ZonedDateTime end;
		public String comment;
	}
}
