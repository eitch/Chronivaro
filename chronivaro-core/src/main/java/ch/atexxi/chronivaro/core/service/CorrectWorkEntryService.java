package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.model.WorkEntryHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class CorrectWorkEntryService
		extends AbstractService<CorrectWorkEntryService.CorrectWorkEntryArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(CorrectWorkEntryArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("workEntryId must be set", arg.workEntryId);
		DBC.PRE.assertNotNull("start must be set", arg.start);
		DBC.PRE.assertNotNull("end must be set", arg.end);
		DBC.PRE.assertNotEmpty("comment must be set", arg.comment);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource workEntry = tx.getResourceBy(TYPE_WORK_ENTRY, arg.workEntryId, true).getClone();
			String employeeId = workEntry.getString(BAG_RELATIONS, PARAM_EMPLOYEE);

			ZonedDateTime oldStart = workEntry.getDate(PARAM_START);
			ZonedDateTime oldEnd = workEntry.getDate(PARAM_END);
			if (oldEnd.getYear() == 1970)
				oldEnd = null;

			if (arg.end.isBefore(arg.start)) {
				throw new IllegalStateException("End time cannot be before start time!");
			}

			WorkEntryHelper.validateNoOverlap(tx, employeeId, arg.start, arg.end, workEntry.getId());

			workEntry.setDate(PARAM_START, arg.start);
			workEntry.setDate(PARAM_END, arg.end);
			workEntry.setString(PARAM_COMMENT, arg.comment);
			workEntry.setString(PARAM_SOURCE, SOURCE_MANUAL);

			tx.update(workEntry);

			if (!arg.start.equals(oldStart)) {
				ChronivaroAuditHelper.audit(tx, TYPE_WORK_ENTRY, workEntry.getId(), PARAM_START, oldStart.toString(),
						arg.start.toString());
			}
			if (oldEnd == null || !arg.end.equals(oldEnd)) {
				ChronivaroAuditHelper.audit(tx, TYPE_WORK_ENTRY, workEntry.getId(), PARAM_END,
						oldEnd == null ? null : oldEnd.toString(), arg.end.toString());
			}

			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	@Override
	public CorrectWorkEntryArgument getArgumentInstance() {
		return new CorrectWorkEntryArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	public static class CorrectWorkEntryArgument extends ServiceArgument {
		public String workEntryId;
		public ZonedDateTime start;
		public ZonedDateTime end;
		public String comment;
	}
}
