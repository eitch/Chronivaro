package ch.eitchnet.chronivaro.web;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class WebAuditLogUiTest {

	private File getWebappDir() {
		File dir = new File("src/main/webapp");
		if (dir.exists()) return dir;
		dir = new File("chronivaro-web/src/main/webapp");
		if (dir.exists()) return dir;
		dir = new File("../chronivaro-web/src/main/webapp");
		if (dir.exists()) return dir;
		throw new IllegalStateException("Could not locate chronivaro-web/src/main/webapp directory");
	}

	@Test
	public void shouldVerifyAuditLogNavigationAndRouting() throws IOException {
		File htmlFile = new File(getWebappDir(), "index.html");
		assertTrue("index.html must exist", htmlFile.exists());
		String html = Files.readString(htmlFile.toPath());

		// Verify audit log navigation item
		assertTrue("index.html must contain audit-log navigation item with Administrator role",
				html.contains("<a href=\"#audit-log\" class=\"nav-link\" data-i18n=\"nav.auditLog\">Audit Log</a>"));

		File appJsFile = new File(getWebappDir(), "js/app.js");
		assertTrue("app.js must exist", appJsFile.exists());
		String appJs = Files.readString(appJsFile.toPath());

		// Verify import and router mapping
		assertTrue("app.js must import AuditLogView", appJs.contains("import AuditLogView from './pages/AuditLogView.js'"));
		assertTrue("app.js must handle route 'audit-log'", appJs.contains("case 'audit-log':"));
		assertTrue("app.js must instantiate AuditLogView", appJs.contains("view = new AuditLogView(this)"));
	}

	@Test
	public void shouldVerifyAuditLogViewAndApiComponents() throws IOException {
		File apiFile = new File(getWebappDir(), "js/api/AuditLogApi.js");
		assertTrue("AuditLogApi.js must exist", apiFile.exists());
		String apiJs = Files.readString(apiFile.toPath());
		assertTrue("AuditLogApi must call admin/audit-logs endpoint", apiJs.contains("rest/chronivaro/v1/admin/audit-logs"));
		assertTrue("AuditLogApi must export getAuditLogs method", apiJs.contains("getAuditLogs("));

		File viewFile = new File(getWebappDir(), "js/pages/AuditLogView.js");
		assertTrue("AuditLogView.js must exist", viewFile.exists());
		String viewJs = Files.readString(viewFile.toPath());

		// Verify view elements: filters, table, pagination, detail modal
		assertTrue("AuditLogView must have audit-from filter", viewJs.contains("id=\"audit-from\""));
		assertTrue("AuditLogView must have audit-to filter", viewJs.contains("id=\"audit-to\""));
		assertTrue("AuditLogView must have audit-entity-type filter", viewJs.contains("id=\"audit-entity-type\""));
		assertTrue("AuditLogView must have audit-entity-id filter", viewJs.contains("id=\"audit-entity-id\""));
		assertTrue("AuditLogView must have audit-username filter", viewJs.contains("id=\"audit-username\""));
		assertTrue("AuditLogView must have audit-action filter", viewJs.contains("id=\"audit-action\""));
		assertTrue("AuditLogView must have data table", viewJs.contains("id=\"audit-table\""));
		assertTrue("AuditLogView must have pagination controls", viewJs.contains("id=\"audit-prev-btn\"") && viewJs.contains("id=\"audit-next-btn\""));
		assertTrue("AuditLogView must have detail inspection modal", viewJs.contains("id=\"audit-detail-modal\""));
	}
}
