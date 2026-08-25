package ch.eitchnet.chronivaro.core.search;

import li.strolch.search.ResourceSearch;
import li.strolch.utils.collections.DateRange;

import java.time.ZonedDateTime;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.search.PredicatesSupport.isAfter;
import static li.strolch.search.PredicatesSupport.isBefore;
import static li.strolch.utils.helper.StringHelper.isNotEmpty;

public class AuditEventSearch extends ResourceSearch {

	public AuditEventSearch() {
		types(TYPE_AUDIT_EVENT);
	}

	public AuditEventSearch forElementType(String elementType) {
		if (isNotEmpty(elementType))
			where(param(BAG_PARAMETERS, PARAM_ELEMENT_TYPE, isEqualTo(elementType)));
		return this;
	}

	public AuditEventSearch forElementId(String elementId) {
		if (isNotEmpty(elementId))
			where(param(BAG_PARAMETERS, PARAM_ELEMENT_ID, isEqualTo(elementId)));
		return this;
	}

	public AuditEventSearch forUsername(String username) {
		if (isNotEmpty(username))
			where(param(BAG_PARAMETERS, PARAM_CREATED_BY, isEqualToIgnoreCase(username)));
		return this;
	}

	public AuditEventSearch forAction(String action) {
		if (isNotEmpty(action))
			where(param(BAG_PARAMETERS, PARAM_ACTION, isEqualToIgnoreCase(action)));
		return this;
	}

	public AuditEventSearch forCorrelationId(String correlationId) {
		if (isNotEmpty(correlationId))
			where(param(BAG_PARAMETERS, PARAM_CORRELATION_ID, isEqualTo(correlationId)));
		return this;
	}

	public AuditEventSearch inDateRange(ZonedDateTime from, ZonedDateTime to) {
		if (from != null && to != null) {
			DateRange range = new DateRange().from(from, true).to(to, true);
			where(param(BAG_PARAMETERS, PARAM_DATE, inRange(range)));
		} else if (from != null) {
			where(param(BAG_PARAMETERS, PARAM_DATE, isAfter(from, true)));
		} else if (to != null) {
			where(param(BAG_PARAMETERS, PARAM_DATE, isBefore(to, true)));
		}
		return this;
	}
}
