package ch.atexxi.chronivaro.core.model;

import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.utils.dbc.DBC;
import org.slf4j.MDC;

import java.time.ZonedDateTime;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.utils.helper.StringHelper.isEmpty;
import static li.strolch.utils.helper.StringHelper.isNotEmpty;

public class ChronivaroAuditHelper {

	public static final String MDC_CORRELATION_ID = "correlationId";
	public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

	private static final ThreadLocal<String> CORRELATION_ID_HOLDER = new ThreadLocal<>();

	public static String getCorrelationId() {
		String id = CORRELATION_ID_HOLDER.get();
		if (isEmpty(id))
			id = MDC.get(MDC_CORRELATION_ID);
		if (isEmpty(id))
			id = MDC.get(HEADER_CORRELATION_ID);
		return id != null ? id : "";
	}

	public static void setCorrelationId(String correlationId) {
		if (correlationId == null) {
			CORRELATION_ID_HOLDER.remove();
		} else {
			CORRELATION_ID_HOLDER.set(correlationId);
		}
	}

	public static void removeCorrelationId() {
		CORRELATION_ID_HOLDER.remove();
	}

	public static void audit(StrolchTransaction tx, String elementType, String elementId, String paramName,
			String oldValue, String newValue) {
		audit(tx, elementType, elementId, null, null, paramName, oldValue, newValue, null, null);
	}

	public static void auditChange(StrolchTransaction tx, String elementType, String elementId, String paramName,
			String oldValue, String newValue) {
		audit(tx, elementType, elementId, null, null, paramName, oldValue, newValue, null, null);
	}

	public static void audit(StrolchTransaction tx, String elementType, String elementId, String action,
			String details) {
		audit(tx, elementType, elementId, action, null, null, null, null, details, null);
	}

	public static void auditAction(StrolchTransaction tx, String elementType, String elementId, String action,
			String reason, String details) {
		audit(tx, elementType, elementId, action, reason, null, null, null, details, null);
	}

	public static void audit(StrolchTransaction tx, String elementType, String elementId, String action,
			String reason, String paramName, String oldValue, String newValue, String details) {
		audit(tx, elementType, elementId, action, reason, paramName, oldValue, newValue, details, null);
	}

	public static void audit(StrolchTransaction tx, String elementType, String elementId, String action,
			String reason, String paramName, String oldValue, String newValue, String details,
			String explicitCorrelationId) {
		DBC.PRE.assertNotNull("tx must be set", tx);
		DBC.PRE.assertNotEmpty("elementType must be set", elementType);
		DBC.PRE.assertNotEmpty("elementId must be set", elementId);

		Resource auditEvent = tx.getResourceTemplate(TYPE_AUDIT_EVENT, true);

		String name = "Audit " + elementType + " " + elementId;
		if (isNotEmpty(action))
			name += " " + action;
		auditEvent.setName(name);

		String resolvedCorrelationId = isNotEmpty(explicitCorrelationId) ? explicitCorrelationId : getCorrelationId();

		auditEvent.setString(PARAM_ELEMENT_TYPE, elementType);
		auditEvent.setString(PARAM_ELEMENT_ID, elementId);
		auditEvent.setString(PARAM_NAME, paramName == null ? "" : paramName);
		auditEvent.setString(PARAM_ACTION, action == null ? "" : action);
		auditEvent.setString(PARAM_REASON, reason == null ? "" : reason);
		auditEvent.setString(PARAM_CORRELATION_ID, resolvedCorrelationId);
		auditEvent.setString(PARAM_DETAILS, details == null ? "" : details);
		auditEvent.setString(PARAM_OLD_VALUE, oldValue == null ? "" : oldValue);
		auditEvent.setString(PARAM_NEW_VALUE, newValue == null ? "" : newValue);
		auditEvent.setDate(PARAM_DATE, ZonedDateTime.now());
		auditEvent.setString(PARAM_CREATED_BY, tx.getCertificate() != null ? tx.getCertificate().getUsername() : "system");

		tx.add(auditEvent);
	}
}
