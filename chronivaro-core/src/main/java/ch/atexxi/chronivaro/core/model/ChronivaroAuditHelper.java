package ch.atexxi.chronivaro.core.model;

import li.strolch.model.ParameterBag;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;
import java.util.UUID;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class ChronivaroAuditHelper {

	public static void audit(StrolchTransaction tx, String elementType, String elementId, String paramName,
			String oldValue, String newValue) {
		DBC.PRE.assertNotEmpty("elementType must be set", elementType);
		DBC.PRE.assertNotEmpty("elementId must be set", elementId);
		DBC.PRE.assertNotEmpty("paramName must be set", paramName);

		Resource auditEvent = new Resource(UUID.randomUUID().toString(), "Audit " + elementType + " " + elementId,
				TYPE_AUDIT_EVENT);
		auditEvent.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
		auditEvent.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));

		auditEvent.setString(PARAM_ELEMENT_TYPE, elementType);
		auditEvent.setString(PARAM_ELEMENT_ID, elementId);
		auditEvent.setString(PARAM_NAME, paramName);
		auditEvent.setString(PARAM_OLD_VALUE, oldValue == null ? "" : oldValue);
		auditEvent.setString(PARAM_NEW_VALUE, newValue == null ? "" : newValue);
		auditEvent.setDate(PARAM_DATE, ZonedDateTime.now());
		auditEvent.setString(PARAM_CREATED_BY, tx.getCertificate().getUsername());

		tx.add(auditEvent);
	}
}
