package ch.eitchnet.chronivaro.rest;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import ch.eitchnet.chronivaro.rest.dto.AuditLogDto;
import ch.eitchnet.chronivaro.rest.dto.ErrorDto;
import ch.eitchnet.chronivaro.rest.dto.PagedResultDto;
import ch.eitchnet.chronivaro.rest.resource.ChronivaroRestHelper;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import org.junit.Before;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.*;

public class AuditLogsResourceTest extends AbstractChronivaroRestfulTest {

	private String adminAuthToken;
	private String employeeAuthToken;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		this.adminAuthToken = authenticate("admin", "admin");
		this.employeeAuthToken = authenticate("employee", "admin");

		Certificate adminCert = runtimeMock.getPrivilegeHandler().authenticate("admin", "admin".toCharArray());
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			// Clear existing audit logs
			for (Resource r : tx.streamResources(TYPE_AUDIT_EVENT).toList()) {
				tx.remove(r);
			}

			// Seed known audit events
			ChronivaroAuditHelper.audit(tx, TYPE_TEAM, "team-audit-1", AUDIT_ACTION_CREATE, "Created Team 1 for testing");
			ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYEE, "emp-audit-1", AUDIT_ACTION_CREATE, "Created Employee 1");
			ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYEE, "emp-audit-1", AUDIT_ACTION_UPDATE, "Updated Employee 1");
			ChronivaroAuditHelper.audit(tx, TYPE_ABSENCE, "abs-audit-1", AUDIT_ACTION_APPROVE, "Approved Vacation");

			tx.commitOnClose();
		}
	}

	@Test
	public void shouldGetAuditLogsAsAdmin() {
		try (Response response = target()
				.path("chronivaro/v1/admin/audit-logs")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", this.adminAuthToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
			assertNotNull(response.getHeaderString("X-Correlation-Id"));

			List<AuditLogDto> logs = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<AuditLogDto>>() {
					}.getType());
			assertFalse(logs.isEmpty());
			assertEquals(4, logs.size());

			AuditLogDto first = logs.getFirst();
			assertNotNull(first.id());
			assertNotNull(first.timestamp());
			assertEquals("admin", first.username());
			assertNotNull(first.action());
			assertNotNull(first.entityType());
			assertNotNull(first.entityId());
			assertNotNull(first.details());
		}
	}

	@Test
	public void shouldFilterAuditLogsByEntityTypeAndAction() {
		try (Response response = target()
				.path("chronivaro/v1/admin/audit-logs")
				.queryParam("entityType", TYPE_EMPLOYEE)
				.queryParam("action", AUDIT_ACTION_CREATE)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", this.adminAuthToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

			List<AuditLogDto> logs = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<AuditLogDto>>() {
					}.getType());
			assertEquals(1, logs.size());
			AuditLogDto log = logs.getFirst();
			assertEquals(TYPE_EMPLOYEE, log.entityType());
			assertEquals(AUDIT_ACTION_CREATE, log.action());
			assertEquals("emp-audit-1", log.entityId());
		}
	}

	@Test
	public void shouldFilterAuditLogsByUsernameAndDateRange() {
		try (Response response = target()
				.path("chronivaro/v1/admin/audit-logs")
				.queryParam("username", "admin")
				.queryParam("from", "2020-01-01")
				.queryParam("to", "2030-12-31")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", this.adminAuthToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

			List<AuditLogDto> logs = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<AuditLogDto>>() {
					}.getType());
			assertEquals(4, logs.size());
		}
	}

	@Test
	public void shouldPaginateAuditLogs() {
		try (Response response = target()
				.path("chronivaro/v1/admin/audit-logs")
				.queryParam("offset", 0)
				.queryParam("limit", 2)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", this.adminAuthToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

			PagedResultDto<AuditLogDto> paged = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<PagedResultDto<AuditLogDto>>() {
					}.getType());
			assertEquals(0, paged.offset());
			assertEquals(2, paged.limit());
			assertEquals(4, paged.total());
			assertEquals(2, paged.size());
			assertEquals(2, paged.data().size());
		}
	}

	@Test
	public void shouldDenyAccessToNonAdminUser() {
		try (Response response = target()
				.path("chronivaro/v1/admin/audit-logs")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", this.employeeAuthToken)
				.get()) {
			assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());

			ErrorDto error = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), ErrorDto.class);
			assertEquals("ACCESS_DENIED", error.errorCode());
			assertNotNull(error.correlationId());
		}
	}

	@Test
	public void shouldRejectUnauthenticatedRequest() {
		try (Response response = target()
				.path("chronivaro/v1/admin/audit-logs")
				.request(MediaType.APPLICATION_JSON)
				.get()) {
			assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
		}
	}

	@Test
	public void shouldRejectInvalidDateFormat() {
		try (Response response = target()
				.path("chronivaro/v1/admin/audit-logs")
				.queryParam("from", "invalid-date-format")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", this.adminAuthToken)
				.get()) {
			assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

			ErrorDto error = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), ErrorDto.class);
			assertEquals("INVALID_DATE_FORMAT", error.errorCode());
			assertNotNull(error.correlationId());
		}
	}

	@Test
	public void shouldGetAuditLogDetailsAndSnapshots() {
		Certificate adminCert = runtimeMock.getPrivilegeHandler().authenticate("admin", "admin".toCharArray());
		try (StrolchTransaction tx = runtimeMock.openUserTx(adminCert, false)) {
			ChronivaroAuditHelper.audit(tx, TYPE_EMPLOYEE, "emp-snapshot-1", AUDIT_ACTION_UPDATE, "Updated target hours",
					"weeklyTargetMinutes", "2400", "2520", "Target minutes adjusted", "test-corr-id-999");
			tx.commitOnClose();
		}

		try (Response response = target()
				.path("chronivaro/v1/admin/audit-logs")
				.queryParam("entityId", "emp-snapshot-1")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", this.adminAuthToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

			List<AuditLogDto> logs = ChronivaroRestHelper
					.createGson()
					.fromJson(response.readEntity(String.class), new TypeToken<List<AuditLogDto>>() {
					}.getType());
			assertEquals(1, logs.size());
			AuditLogDto log = logs.getFirst();
			assertEquals("emp-snapshot-1", log.entityId());
			assertEquals("Updated target hours", log.reason());
			assertEquals("weeklyTargetMinutes", log.paramName());
			assertEquals("2400", log.oldValue());
			assertEquals("2520", log.newValue());
			assertEquals("test-corr-id-999", log.correlationId());
			assertEquals("Target minutes adjusted", log.details());
		}
	}
}
