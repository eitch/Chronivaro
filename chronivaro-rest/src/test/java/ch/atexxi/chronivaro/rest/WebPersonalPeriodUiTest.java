package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.rest.dto.MonthSummaryDto;
import ch.atexxi.chronivaro.rest.dto.PeriodActionRequestDto;
import ch.atexxi.chronivaro.rest.dto.PeriodStatusDto;
import ch.atexxi.chronivaro.rest.resource.ChronivaroRestHelper;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class WebPersonalPeriodUiTest extends AbstractChronivaroRestfulTest {

	@Test
	public void shouldContainCompleteWebAssetsForPersonalPeriodsAndMonthlyClosing() throws IOException {
		File webDir = new File("../chronivaro-web/src/main/webapp");
		if (!webDir.exists()) {
			webDir = new File("chronivaro-web/src/main/webapp");
		}
		assertTrue("Web directory must exist", webDir.exists());

		File indexHtml = new File(webDir, "index.html");
		assertTrue("index.html must exist", indexHtml.exists());
		String indexContent = Files.readString(indexHtml.toPath());
		assertTrue("index.html must have my-periods nav link", indexContent.contains("href=\"#my-periods\""));
		assertTrue("index.html must have Monthly Closing label", indexContent.contains("Monthly Closing"));

		File appJs = new File(webDir, "js/app.js");
		assertTrue("app.js must exist", appJs.exists());
		String appJsContent = Files.readString(appJs.toPath());
		assertTrue("app.js must import MyPeriodsView", appJsContent.contains("MyPeriodsView"));
		assertTrue("app.js must route to my-periods", appJsContent.contains("case 'my-periods':"));

		File periodApiJs = new File(webDir, "js/api/PeriodApi.js");
		assertTrue("PeriodApi.js must exist", periodApiJs.exists());
		String periodApiContent = Files.readString(periodApiJs.toPath());
		assertTrue("PeriodApi must provide getMyPeriodStatus", periodApiContent.contains("getMyPeriodStatus"));
		assertTrue("PeriodApi must provide submitMyPeriod", periodApiContent.contains("submitMyPeriod"));
		assertTrue("PeriodApi must provide getMonthSummary", periodApiContent.contains("getMonthSummary"));

		File myPeriodsViewJs = new File(webDir, "js/pages/MyPeriodsView.js");
		assertTrue("MyPeriodsView.js must exist", myPeriodsViewJs.exists());
		String viewContent = Files.readString(myPeriodsViewJs.toPath());
		assertTrue("View must contain period month picker", viewContent.contains("period-month-picker"));
		assertTrue("View must contain month navigation buttons", viewContent.contains("prev-period-btn") && viewContent.contains("next-period-btn"));
		assertTrue("View must contain status banner", viewContent.contains("period-status-banner"));
		assertTrue("View must contain status badge", viewContent.contains("period-status-badge"));
		assertTrue("View must contain rejection alert", viewContent.contains("rejection-alert"));
		assertTrue("View must contain summary cards", viewContent.contains("period-cards-grid"));
		assertTrue("View must contain calculation snapshot section", viewContent.contains("calculation-snapshot-container"));
		assertTrue("View must contain daily breakdown table", viewContent.contains("daily-breakdown-table"));
		assertTrue("View must contain period submission section", viewContent.contains("period-submission-content"));
	}

	@Test
	public void shouldExecutePersonalPeriodWorkflowAndFetchSummary() {
		String employeeToken = authenticate("employee", "admin");
		String supervisorToken = authenticate("supervisor", "admin");
		String yearMonth = "2025-08";

		// 1. Fetch Month Summary
		try (Response summaryRes = target().path("/chronivaro/v1/me/month-summary/" + yearMonth)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), summaryRes.getStatus());
			MonthSummaryDto summaryDto = ChronivaroRestHelper.createGson().fromJson(
					summaryRes.readEntity(String.class), MonthSummaryDto.class);
			assertNotNull("Month summary must not be null", summaryDto);
			assertEquals("employee_emp", summaryDto.employeeId());
			assertNotNull("Day summaries must not be null", summaryDto.daySummaries());
			assertFalse("Day summaries must contain month days", summaryDto.daySummaries().isEmpty());
		}

		// 2. Fetch Initial Period Status (defaults to OPEN)
		try (Response statusRes = target().path("/chronivaro/v1/me/periods/" + yearMonth)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), statusRes.getStatus());
			PeriodStatusDto statusDto = ChronivaroRestHelper.createGson().fromJson(
					statusRes.readEntity(String.class), PeriodStatusDto.class);
			assertNotNull("Status DTO must not be null", statusDto);
			assertEquals("employee_emp", statusDto.employeeId());
			assertEquals(yearMonth, statusDto.yearMonth());
			assertEquals("OPEN", statusDto.status());
		}

		// 3. Submit Period for Approval
		PeriodActionRequestDto submitReq = new PeriodActionRequestDto("employee_emp", yearMonth, "August closing ready");
		try (Response submitRes = target().path("/chronivaro/v1/me/periods/" + yearMonth + "/submit")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.post(Entity.json(ChronivaroRestHelper.createGson().toJson(submitReq)))) {
			assertEquals(Response.Status.OK.getStatusCode(), submitRes.getStatus());
			PeriodStatusDto submittedDto = ChronivaroRestHelper.createGson().fromJson(
					submitRes.readEntity(String.class), PeriodStatusDto.class);
			assertEquals("SUBMITTED", submittedDto.status());
			assertNotNull("Submitted date must be set", submittedDto.submittedAt());
			assertNotNull("Calculation snapshot must be set", submittedDto.calculationSnapshot());
			assertEquals("August closing ready", submittedDto.comment());
		}

		// 4. Supervisor Rejects with Comment
		PeriodActionRequestDto rejectReq = new PeriodActionRequestDto("employee_emp", yearMonth, "Please correct time on Aug 12");
		try (Response rejectRes = target().path("/chronivaro/v1/periods/reject")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", supervisorToken)
				.post(Entity.json(ChronivaroRestHelper.createGson().toJson(rejectReq)))) {
			assertEquals(Response.Status.OK.getStatusCode(), rejectRes.getStatus());
			PeriodStatusDto rejectedDto = ChronivaroRestHelper.createGson().fromJson(
					rejectRes.readEntity(String.class), PeriodStatusDto.class);
			assertEquals("REJECTED", rejectedDto.status());
			assertEquals("Please correct time on Aug 12", rejectedDto.comment());
			assertEquals("supervisor", rejectedDto.rejectedBy());
			assertNotNull("Rejected timestamp must be set", rejectedDto.rejectedAt());
		}

		// 5. Employee queries rejected status to see rejection reason
		try (Response getRejectedRes = target().path("/chronivaro/v1/me/periods/" + yearMonth)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), getRejectedRes.getStatus());
			PeriodStatusDto rejectedDto = ChronivaroRestHelper.createGson().fromJson(
					getRejectedRes.readEntity(String.class), PeriodStatusDto.class);
			assertEquals("REJECTED", rejectedDto.status());
			assertEquals("Please correct time on Aug 12", rejectedDto.comment());
			assertEquals("supervisor", rejectedDto.rejectedBy());
		}

		// 6. Employee re-submits period
		PeriodActionRequestDto resubmitReq = new PeriodActionRequestDto("employee_emp", yearMonth, "Updated Aug 12 hours");
		try (Response resubmitRes = target().path("/chronivaro/v1/me/periods/" + yearMonth + "/submit")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.post(Entity.json(ChronivaroRestHelper.createGson().toJson(resubmitReq)))) {
			assertEquals(Response.Status.OK.getStatusCode(), resubmitRes.getStatus());
			PeriodStatusDto resubmittedDto = ChronivaroRestHelper.createGson().fromJson(
					resubmitRes.readEntity(String.class), PeriodStatusDto.class);
			assertEquals("SUBMITTED", resubmittedDto.status());
		}

		// 7. Supervisor approves period
		PeriodActionRequestDto approveReq = new PeriodActionRequestDto("employee_emp", yearMonth, "Approved");
		try (Response approveRes = target().path("/chronivaro/v1/periods/approve")
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", supervisorToken)
				.post(Entity.json(ChronivaroRestHelper.createGson().toJson(approveReq)))) {
			assertEquals(Response.Status.OK.getStatusCode(), approveRes.getStatus());
			PeriodStatusDto approvedDto = ChronivaroRestHelper.createGson().fromJson(
					approveRes.readEntity(String.class), PeriodStatusDto.class);
			assertEquals("APPROVED", approvedDto.status());
			assertEquals("supervisor", approvedDto.approvedBy());
			assertNotNull("Approved timestamp must be set", approvedDto.approvedAt());
		}

		// 8. Employee verifies approved status
		try (Response getApprovedRes = target().path("/chronivaro/v1/me/periods/" + yearMonth)
				.request(MediaType.APPLICATION_JSON)
				.header("Authorization", employeeToken)
				.get()) {
			assertEquals(Response.Status.OK.getStatusCode(), getApprovedRes.getStatus());
			PeriodStatusDto approvedDto = ChronivaroRestHelper.createGson().fromJson(
					getApprovedRes.readEntity(String.class), PeriodStatusDto.class);
			assertEquals("APPROVED", approvedDto.status());
			assertEquals("supervisor", approvedDto.approvedBy());
		}
	}
}
