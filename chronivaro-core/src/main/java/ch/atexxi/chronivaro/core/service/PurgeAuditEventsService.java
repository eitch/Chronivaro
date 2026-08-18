package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.search.AuditEventSearch;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;
import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.search.ExpressionsSupport.param;
import static li.strolch.search.PredicatesSupport.isBefore;

public class PurgeAuditEventsService
		extends AbstractService<PurgeAuditEventsService.PurgeAuditEventsArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(PurgeAuditEventsArgument arg) throws Exception {
		DBC.PRE.assertNotNull("Argument must be provided", arg);
		ZonedDateTime cutoff = arg.cutoffDate;
		if (cutoff == null) {
			DBC.PRE.assertNotNull("Either cutoffDate or retentionDays must be provided", arg.retentionDays);
			DBC.PRE.assertTrue("retentionDays must be > 0", arg.retentionDays > 0);
			cutoff = ZonedDateTime.now().minusDays(arg.retentionDays);
		}

		int purgedCount;
		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			List<Resource> eventsToPurge = new AuditEventSearch()
					.internal()
					.where(param(BAG_PARAMETERS, PARAM_DATE, isBefore(cutoff, false)))
					.search(tx)
					.toList();

			purgedCount = eventsToPurge.size();
			for (Resource event : eventsToPurge) {
				tx.remove(event);
			}

			if (purgedCount > 0) {
				ChronivaroAuditHelper.auditAction(tx, TYPE_AUDIT_EVENT, "retention-purge", AUDIT_ACTION_PURGE,
						"Retention policy purge", "Purged " + purgedCount + " audit records before " + cutoff);
			}

			tx.commitOnClose();
		}

		return ServiceResult.success("Purged " + purgedCount + " audit events.");
	}

	@Override
	public PurgeAuditEventsArgument getArgumentInstance() {
		return new PurgeAuditEventsArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	public static class PurgeAuditEventsArgument extends ServiceArgument {
		public Integer retentionDays;
		public ZonedDateTime cutoffDate;

		public PurgeAuditEventsArgument() {
		}

		public PurgeAuditEventsArgument(int retentionDays) {
			this.retentionDays = retentionDays;
		}

		public PurgeAuditEventsArgument(ZonedDateTime cutoffDate) {
			this.cutoffDate = cutoffDate;
		}
	}
}
