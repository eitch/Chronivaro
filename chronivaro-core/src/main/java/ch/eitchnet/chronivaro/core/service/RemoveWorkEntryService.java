package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.core.model.ChronivaroModelHelper;
import ch.eitchnet.chronivaro.core.model.PeriodHelper;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.StringArgument;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;
import java.util.Optional;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;

public class RemoveWorkEntryService extends AbstractService<StringArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(StringArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("workEntryId must be set", arg.value);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource workEntry = tx.getResourceBy(TYPE_WORK_ENTRY, arg.value, true);
			String employeeId = workEntry.getRelationId(PARAM_EMPLOYEE);

			boolean isAdminOrHr = tx.getPrivilegeContext().hasRole(ROLE_HR)
					|| tx.getPrivilegeContext().hasRole(ROLE_ADMIN)
					|| tx.getPrivilegeContext().hasRole(ROLE_ADMINISTRATOR);

			if (!isAdminOrHr) {
				Optional<Resource> callerEmployee = ChronivaroModelHelper.findEmployeeByUser(tx, tx.getCertificate().getUserId());
				boolean isSelf = callerEmployee.isPresent() && callerEmployee.get().getId().equals(employeeId);

				if (!isSelf) {
					ChronivaroModelHelper.assertCanManageEmployee(tx, employeeId);
				}
			}

			ZonedDateTime start = workEntry.getDate(PARAM_START);
			ZonedDateTime end = workEntry.getDate(PARAM_END);
			PeriodHelper.assertPeriodOpen(tx, employeeId, start.toLocalDate());

			if (workEntry.hasRelation(PARAM_WORK_DAY)) {
				Resource workDay = tx.getResourceByRelation(workEntry, PARAM_WORK_DAY, false);
				if (workDay != null && workDay.hasParameter(BAG_RELATIONS, PARAM_WORK_ENTRIES)) {
					workDay.getStringListP(BAG_RELATIONS, PARAM_WORK_ENTRIES).removeValue(workEntry.getId());
					tx.update(workDay);
				}
			}

			tx.remove(workEntry);
			ChronivaroAuditHelper.audit(tx, TYPE_WORK_ENTRY, workEntry.getId(), AUDIT_ACTION_REMOVE,
					"Removed work entry " + workEntry.getId() + " for employee " + employeeId + " (" + start + " to " + end + ")");

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
