package ch.atexxi.chronivaro.core;

import ch.atexxi.chronivaro.core.model.ChronivaroAuditHelper;
import ch.atexxi.chronivaro.core.search.AuditEventSearch;
import ch.atexxi.chronivaro.core.service.PurgeAuditEventsService;
import li.strolch.exception.StrolchAccessDeniedException;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.MDC;

import java.time.ZonedDateTime;
import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class AuditEventTest {

	private static RuntimeMock runtimeMock;
	private static Certificate adminCert;
	private static Certificate employeeCert;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + AuditEventTest.class.getSimpleName(),
				"src/test/resources");
		runtimeMock.startContainer();
		adminCert = runtimeMock.login("admin", "admin");
		employeeCert = runtimeMock.login("employee", "admin");
	}

	@AfterClass
	public static void afterClass() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	@Before
	public void cleanAuditEvents() {
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			List<Resource> existing = tx.streamResources(TYPE_AUDIT_EVENT).toList();
			for (Resource r : existing) {
				tx.remove(r);
			}
			tx.commitOnClose();
		}
		ChronivaroAuditHelper.removeCorrelationId();
		MDC.remove("correlationId");
		MDC.remove("X-Correlation-Id");
	}

	@Test
	public void shouldCreateAuditEventWithCompleteMetadata() {
		String correlationId = "corr-12345";
		ChronivaroAuditHelper.setCorrelationId(correlationId);

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYEE, "emp01", AUDIT_ACTION_UPDATE, "Department change",
					PARAM_PRIMARY_TEAM, "Team-Old", "Team-New", "Moved employee to team New");
			tx.commitOnClose();
		}

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forElementType(TYPE_EMPLOYEE)
					.forElementId("emp01")
					.search(tx)
					.toList();

			assertEquals(1, events.size());
			Resource event = events.get(0);
			assertEquals(TYPE_EMPLOYEE, event.getString(PARAM_ELEMENT_TYPE));
			assertEquals("emp01", event.getString(PARAM_ELEMENT_ID));
			assertEquals(AUDIT_ACTION_UPDATE, event.getString(PARAM_ACTION));
			assertEquals("Department change", event.getString(PARAM_REASON));
			assertEquals(PARAM_PRIMARY_TEAM, event.getString(PARAM_NAME));
			assertEquals("Team-Old", event.getString(PARAM_OLD_VALUE));
			assertEquals("Team-New", event.getString(PARAM_NEW_VALUE));
			assertEquals("Moved employee to team New", event.getString(PARAM_DETAILS));
			assertEquals(correlationId, event.getString(PARAM_CORRELATION_ID));
			assertEquals("admin", event.getString(PARAM_CREATED_BY));
			assertNotNull(event.getDate(PARAM_DATE));
		}
	}

	@Test
	public void shouldCaptureCorrelationIdFromMdc() {
		MDC.put("correlationId", "mdc-corr-999");

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			ChronivaroAuditHelper.audit(tx, TYPE_LOCATION, "loc01", AUDIT_ACTION_CREATE, "Created new branch");
			tx.commitOnClose();
		}

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch()
					.forCorrelationId("mdc-corr-999")
					.search(tx)
					.toList();

			assertEquals(1, events.size());
			assertEquals("loc01", events.get(0).getString(PARAM_ELEMENT_ID));
		}
	}

	@Test
	public void shouldFilterByActionAndUserAndDateRange() {
		ZonedDateTime now = ZonedDateTime.now();

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			ChronivaroAuditHelper.audit(tx, TYPE_TEAM, "team01", AUDIT_ACTION_CREATE, "Created Team 1");
			ChronivaroAuditHelper.audit(tx, TYPE_TEAM, "team02", AUDIT_ACTION_UPDATE, "Updated Team 2");
			ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE, "abs01", AUDIT_ACTION_APPROVE, "Approved Absence");
			tx.commitOnClose();
		}

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> creates = new AuditEventSearch()
					.forAction(AUDIT_ACTION_CREATE)
					.search(tx)
					.toList();
			assertEquals(1, creates.size());
			assertEquals("team01", creates.get(0).getString(PARAM_ELEMENT_ID));

			List<Resource> userEvents = new AuditEventSearch()
					.forUsername("admin")
					.search(tx)
					.toList();
			assertEquals(3, userEvents.size());

			List<Resource> dateRangeEvents = new AuditEventSearch()
					.inDateRange(now.minusHours(1), now.plusHours(1))
					.search(tx)
					.toList();
			assertEquals(3, dateRangeEvents.size());

			List<Resource> emptyRangeEvents = new AuditEventSearch()
					.inDateRange(now.plusDays(1), now.plusDays(2))
					.search(tx)
					.toList();
			assertEquals(0, emptyRangeEvents.size());
		}
	}

	@Test
	public void shouldPurgeAgedAuditEvents() {
		ZonedDateTime pastDate = ZonedDateTime.now().minusDays(45);

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYEE, "old-emp", AUDIT_ACTION_CREATE, "Old record");
			tx.commitOnClose();
		}

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			Resource oldEvent = tx.streamResources(TYPE_AUDIT_EVENT)
					.filter(e -> "old-emp".equals(e.getString(PARAM_ELEMENT_ID)))
					.findFirst()
					.orElseThrow();
			oldEvent = tx.readLock(oldEvent);
			oldEvent.setDate(PARAM_DATE, pastDate);
			tx.update(oldEvent);

			ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYEE, "new-emp", AUDIT_ACTION_CREATE, "New record");
			tx.commitOnClose();
		}

		// Purge records older than 30 days
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		PurgeAuditEventsService.PurgeAuditEventsArgument arg = new PurgeAuditEventsService.PurgeAuditEventsArgument(30);
		ServiceResult result = serviceHandler.doService(adminCert, new PurgeAuditEventsService(), arg);
		assertTrue(result.getMessage(), result.isOk());

		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, true)) {
			List<Resource> events = new AuditEventSearch().search(tx).toList();
			// We expect the old record to be purged, the new record to remain, plus the purge audit event itself
			boolean hasOld = events.stream().anyMatch(e -> "old-emp".equals(e.getString(PARAM_ELEMENT_ID)));
			boolean hasNew = events.stream().anyMatch(e -> "new-emp".equals(e.getString(PARAM_ELEMENT_ID)));
			boolean hasPurgeAudit = events.stream().anyMatch(e -> AUDIT_ACTION_PURGE.equals(e.getString(PARAM_ACTION)));

			assertFalse("Old audit event should be purged", hasOld);
			assertTrue("New audit event should remain", hasNew);
			assertTrue("Purge action itself should be audited", hasPurgeAudit);
		}
	}

	@Test
	public void shouldDenyNonAdminFromSearchingAuditEvents() {
		try (StrolchTransaction tx = runtimeMock.openUserTx(employeeCert, true)) {
			assertThrows(StrolchAccessDeniedException.class, () -> {
				new AuditEventSearch().search(tx).toList();
			});
		}
	}

	@Test
	public void shouldDenyNonAdminFromRunningPurgeService() {
		ServiceHandler serviceHandler = runtimeMock.getServiceHandler();
		PurgeAuditEventsService.PurgeAuditEventsArgument arg = new PurgeAuditEventsService.PurgeAuditEventsArgument(30);
		ServiceResult result = serviceHandler.doService(employeeCert, new PurgeAuditEventsService(), arg);
		assertFalse("Non-admin user must not be able to execute PurgeAuditEventsService", result.isOk());
	}
}
